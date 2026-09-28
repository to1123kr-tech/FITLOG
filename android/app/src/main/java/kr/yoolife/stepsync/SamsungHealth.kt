package kr.yoolife.stepsync

import android.app.Activity
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 삼성헬스 Data SDK로 일별 걸음수를 읽는다. **과거 채우기 전용.**
 *
 * 왜 Health Connect 말고 이걸 쓰나:
 *  - Health Connect에는 연결을 켠 시점부터 약 30일치만 넘어온다. 그 이전은
 *    애초에 존재하지 않아서 가져올 방법이 없었다. 삼성헬스 자기 저장소에는
 *    몇 달치가 그대로 살아 있다. (DataViewer로 2월치까지 확인함)
 *  - 삼성헬스가 계산한 일별 총합을 그대로 준다. Health Connect 집계처럼
 *    경계에 걸친 기록을 쪼개면서 정수 버림이 생기지 않는다.
 *
 * ⚠️ **전제: 삼성헬스 개발자 모드가 켜져 있어야 한다.**
 * 삼성헬스 → 설정 → 삼성헬스 정보 → 버전 줄 10번 탭 →
 * 「개발자 모드 (Samsung Health Data SDK)」 → 데이터 읽기용 개발자 모드 ON.
 * 파트너 승인은 앱을 배포할 때만 필요하다. 개인 사이드로딩이라 읽기는 이걸로 된다.
 * 꺼지면 이 기능만 멈춘다. 매일 동기화는 Health Connect라 영향 없다.
 *
 * ⚠️ 백그라운드에서도 읽히는지는 확인 안 됐다. 그래서 **사용자가 버튼을 누르는
 * 포그라운드 작업(과거 채우기)에만** 쓴다. WorkManager 쪽에는 붙이지 말 것.
 */
object SamsungHealth {

    private val DAY = DateTimeFormatter.ISO_LOCAL_DATE

    private val PERMISSIONS: Set<Permission> =
        setOf(Permission.of(DataTypes.STEPS, AccessType.READ))

    /** 권한이 없거나 개발자 모드가 꺼져 있을 때 */
    class NotAllowed(message: String) : Exception(message)

    /**
     * 오늘부터 [days]일 전까지의 **일별 총 걸음수**를 돌려준다.
     * 걸음수 0인 날은 제외한다 (수동 입력값을 덮어쓰지 않기 위해).
     * 반환: `("2026-09-28", 3128)` 꼴, 날짜 오름차순.
     *
     * [activity] 는 Context 가 아니라 **Activity** 여야 한다. 권한 팝업을 띄우는
     * requestPermissions 가 Activity 를 요구한다. (삼성 API 문서에는 Context 로
     * 적혀 있지만 실제 시그니처가 다르다 — 컴파일 에러로 확인)
     * 이 제약 때문에도 백그라운드(WorkManager)에는 붙일 수 없다.
     */
    suspend fun dailySteps(activity: Activity, days: Int): List<Pair<String, Long>> {
        val store = HealthDataService.getStore(activity)

        if (!store.getGrantedPermissions(PERMISSIONS).containsAll(PERMISSIONS)) {
            store.requestPermissions(PERMISSIONS, activity)
            if (!store.getGrantedPermissions(PERMISSIONS).containsAll(PERMISSIONS)) {
                throw NotAllowed(
                    "삼성헬스 걸음수 읽기 권한이 없습니다.\n\n" +
                        "삼성헬스 → 설정 → 삼성헬스 정보 →\n" +
                        "버전 줄 10번 탭 → 개발자 모드 →\n" +
                        "「데이터 읽기용 개발자 모드」가\n켜져 있는지 확인해주세요."
                )
            }
        }

        val today = LocalDate.now()
        val request = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(
                LocalTimeFilter.of(
                    today.minusDays(days.toLong()).atStartOfDay(),
                    today.plusDays(1).atStartOfDay()
                ),
                LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)
            )
            .build()

        return store.aggregateData(request).dataList
            .mapNotNull { d ->
                val steps = (d.value as? Number)?.toLong() ?: 0L
                if (steps <= 0L) null
                else d.getStartLocalDateTime().toLocalDate().format(DAY) to steps
            }
            .sortedBy { it.first }   // "YYYY-MM-DD" 라 문자열 정렬로 날짜순이 된다
    }
}
