package kr.yoolife.stepsync

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private lateinit var status: TextView

    private val requestPerms = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(StepsSync.REQUIRED)) {
            syncNow()
        } else {
            status.text = "걸음수 읽기 권한이 없습니다.\n" +
                "Health Connect → 앱 권한 → 걸음수 동기화 에서 허용해주세요."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            text = "준비 중..."
        }
        val button = Button(this).apply {
            text = "지금 동기화"
            setOnClickListener { start() }
        }
        val backfillButton = Button(this).apply {
            text = "지난 90일 가져오기"
            setOnClickListener { backfill(90) }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            addView(
                status,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 64 }
            )
            addView(button)
            addView(backfillButton)
        })

        SyncWorker.schedule(this)
        start()
    }

    /** 권한을 확인하고, 있으면 바로 동기화 / 없으면 요청 */
    private fun start() {
        if (HealthConnectClient.getSdkStatus(this) != HealthConnectClient.SDK_AVAILABLE) {
            status.text = "이 기기에서는 Health Connect를 쓸 수 없습니다."
            return
        }
        status.text = "권한 확인 중..."
        lifecycleScope.launch {
            val granted = try {
                HealthConnectClient.getOrCreate(this@MainActivity)
                    .permissionController.getGrantedPermissions()
            } catch (e: Exception) {
                status.text = "Health Connect 연결 실패\n${e.message}"
                return@launch
            }
            if (granted.containsAll(StepsSync.REQUIRED)) syncNow()
            else requestPerms.launch(StepsSync.ALL)
        }
    }

    private fun syncNow() {
        status.text = "동기화 중..."
        lifecycleScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    StepsSync.run(this@MainActivity)
                } catch (e: Exception) {
                    "실패\n${e.message}"
                }
            }
            status.text = msg
        }
    }

    /** 지난 기록 일괄 전송 */
    private fun backfill(days: Int) {
        status.text = "지난 ${days}일 확인 중...\n조금 걸립니다"
        lifecycleScope.launch {
            val msg = withContext(Dispatchers.IO) {
                try {
                    StepsSync.backfill(this@MainActivity, days)
                } catch (e: Exception) {
                    "실패\n${e.message}"
                }
            }
            status.text = msg
        }
    }
}
