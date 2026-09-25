package kr.yoolife.stepsync

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

/** Health Connect가 "이 앱이 왜 걸음수를 읽는지" 보여줄 때 여는 화면 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            textSize = 16f
            setPadding(64, 96, 64, 64)
            text = "이 앱은 삼성헬스의 걸음수를 읽어 개인 운동 기록 앱으로 보냅니다.\n\n" +
                "읽는 데이터: 걸음수\n" +
                "보내는 곳: 본인의 Firebase 저장소\n\n" +
                "다른 용도로 쓰거나 제3자에게 제공하지 않습니다."
        })
    }
}
