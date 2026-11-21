package com.jawaker.solitaire

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.jawaker.solitaire.databinding.ActivityMainBinding
import com.jawaker.solitaire.service.AutoTouchAccessibilityService
import com.jawaker.solitaire.service.CardVisionEngine
import com.jawaker.solitaire.service.JawakerAutoSolver
import com.jawaker.solitaire.service.ScreenCaptureService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var projectionManager: MediaProjectionManager
    private val vision = CardVisionEngine()
    private var botJob: Job? = null
    private var isRunning = false
    private var isPaused = false
    private var moveCount = 0
    private var startTime = 0L
    private var pausedDuration = 0L
    private var pauseStartedAt = 0L
    private val recentMessages = ArrayDeque<String>()

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            isRunning = true
            isPaused = false
            moveCount = 0
            startTime = System.currentTimeMillis()
            pausedDuration = 0L
            updateControls()
            showStatus("تم السماح بقراءة الشاشة. افتح لعبة السوليتير واتركها ظاهرة.")
            startBotLoop()
        } else {
            showStatus("لم يتم منح إذن التقاط الشاشة. لم يبدأ البوت.")
            isRunning = false
            updateControls()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        binding.btnStart.setOnClickListener { requestStart() }
        binding.btnStop.setOnClickListener { stopBot("تم إيقاف البوت.") }
        binding.btnPause.setOnClickListener { togglePause() }
        binding.seekSpeed.max = 10
        binding.seekSpeed.progress = 5
        binding.txtSpeedValue.text = "5"
        binding.seekSpeed.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.txtSpeedValue.text = progress.toString()
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        updateControls()
        showStatus("جاهز. فعّل خدمة إمكانية الوصول ثم اضغط ابدأ.")
    }

    private fun requestStart() {
        if (isRunning) return
        if (!isAccessibilityServiceEnabled()) {
            showStatus("فعّل خدمة Solitaire Pova من إعدادات إمكانية الوصول، ثم ارجع واضغط ابدأ.")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (AutoTouchAccessibilityService.active == null) {
            showStatus("خدمة اللمس لم تتصل بعد. انتظر لحظة ثم اضغط ابدأ.")
            return
        }
        showStatus("وافق على نافذة تسجيل الشاشة لتمكين قراءة الرقعة.")
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, AutoTouchAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    private fun startBotLoop() {
        botJob?.cancel()
        botJob = lifecycleScope.launch {
            val solver = JawakerAutoSolver()
            var previousFingerprint = ""
            var unchangedScans = 0
            while (isActive && isRunning) {
                if (isPaused) {
                    delay(250)
                    updateTimer()
                    continue
                }

                val frame = ScreenCaptureService.copyLatestFrame()
                if (frame == null) {
                    showStatus("بانتظار أول لقطة من اللعبة...")
                    delay(400)
                    continue
                }

                val width = frame.width
                val height = frame.height
                val gameState = try {
                    vision.analyzeScreen(frame)
                } catch (error: Exception) {
                    showStatus("تعذرت قراءة الشاشة: ${error.message ?: "خطأ OCR"}")
                    delay(900)
                    null
                } finally {
                    frame.recycle()
                }

                if (gameState == null) continue
                updateGameStats(gameState.recognizedCardCount, moveCount)
                if (gameState.recognizedCardCount < 3 || gameState.tableauColumns.count { it.cards.isNotEmpty() } < 2) {
                    showStatus("لم أتعرف على الرقعة بشكل كافٍ. اترك اللعبة ظاهرة وبحجمها الكامل؛ لن أنفذ حركة غير مؤكدة.")
                    delay(1200)
                    continue
                }

                if (gameState.boardFingerprint == previousFingerprint) {
                    unchangedScans++
                } else {
                    unchangedScans = 0
                    previousFingerprint = gameState.boardFingerprint
                }
                if (unchangedScans >= 3) {
                    stopBot("أوقفت التنفيذ لأن صورة الرقعة لم تتغير بعد المحاولات؛ تجنباً لتكرار اللمس على نفس المكان.")
                    break
                }

                val move = solver.decideNextMove(gameState)
                if (move == null) {
                    showStatus("لم أجد حركة قانونية مؤكدة من البطاقات المقروءة. أوقفت اللعب دون سحب ورقة عشوائياً.")
                    stopBot(null)
                    break
                }

                val service = AutoTouchAccessibilityService.active
                if (service == null) {
                    stopBot("خدمة إمكانية الوصول غير متصلة. فعّل الخدمة ثم أعد التشغيل.")
                    break
                }

                showStatus("الحركة ${moveCount + 1}: ${move.description}")
                val success = try {
                    service.performMove(move, width, height)
                } catch (error: Exception) {
                    showStatus("فشل تنفيذ الحركة: ${error.message}")
                    false
                }
                if (!success) {
                    stopBot("لم يؤكد Android نجاح اللمس/السحب. أوقفت البوت قبل الحركة التالية.")
                    break
                }
                moveCount++
                updateGameStats(gameState.recognizedCardCount, moveCount)
                delay((1500 - binding.seekSpeed.progress * 90).coerceIn(550, 1450).toLong())
                updateTimer()
            }
        }
    }

    private fun togglePause() {
        if (!isRunning) return
        isPaused = !isPaused
        if (isPaused) pauseStartedAt = System.currentTimeMillis()
        else if (pauseStartedAt > 0) {
            pausedDuration += System.currentTimeMillis() - pauseStartedAt
            pauseStartedAt = 0L
        }
        binding.btnPause.text = if (isPaused) "استئناف" else "إيقاف مؤقت"
        showStatus(if (isPaused) "البوت متوقف مؤقتاً." else "استؤنف تحليل الرقعة.")
        updateControls()
    }

    private fun stopBot(message: String?) {
        isRunning = false
        isPaused = false
        botJob?.cancel()
        botJob = null
        ScreenCaptureService.stop(this)
        if (message != null) showStatus(message)
        updateControls()
    }

    private fun updateControls() {
        if (!::binding.isInitialized) return
        binding.btnStart.isEnabled = !isRunning
        binding.btnStop.isEnabled = isRunning
        binding.btnPause.isEnabled = isRunning
        binding.btnPause.text = if (isPaused) "استئناف" else "إيقاف مؤقت"
    }

    private fun showStatus(message: String) {
        if (!::binding.isInitialized) return
        runOnUiThread {
            binding.txtStatus.text = message
            binding.txtError.text = if (message.contains("فشل") || message.contains("تعذرت") || message.contains("أوقفت")) message else ""
            recentMessages.addFirst(message)
            while (recentMessages.size > 3) recentMessages.removeLast()
            binding.txtShortErrors.text = recentMessages.joinToString("\n")
        }
    }

    private fun updateGameStats(recognized: Int, moves: Int) {
        runOnUiThread {
            binding.txtFoundations.text = "بطاقات مقروءة: $recognized"
            binding.txtMoves.text = "الحركات: $moves"
        }
    }

    private fun updateTimer() {
        if (startTime == 0L) return
        val now = System.currentTimeMillis()
        val paused = if (isPaused && pauseStartedAt > 0) now - pauseStartedAt else 0L
        val seconds = ((now - startTime - pausedDuration - paused) / 1000).coerceAtLeast(0)
        val value = String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
        runOnUiThread { binding.txtTimer.text = "الوقت: $value" }
    }

    override fun onDestroy() {
        stopBot(null)
        vision.close()
        super.onDestroy()
    }
}
