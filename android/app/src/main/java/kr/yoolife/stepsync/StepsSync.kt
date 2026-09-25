package kr.yoolife.stepsync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter

/**
 * Health Connect에서 오늘 걸음수를 읽어 Firestore에 올린다.
 *
 * 웹앱(index.html)이 기대하는 문서 형태와 동일하게 맞춘다:
 *   { id, dateStr:"YYYY-MM-DD", ampm:"am", type:"steps", steps:Int, ts:Long }
 * 문서 id를 날짜로 고정하므로 하루에 몇 번 돌려도 같은 문서가 갱신된다.
 */
object StepsSync {

    // 이미 웹앱(index.html)에 공개되어 있는 값과 동일한 키
    private const val API_KEY = "AIzaSyAYVVjoGbe7Tez7skPJgU0wdUhzY4EVhvo"
    private const val PROJECT = "yoo-life"
    private const val COLLECTION = "workout"

    /** 없으면 동작 자체가 불가능한 권한 */
    val REQUIRED: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    /** 없어도 앱을 열었을 때는 동작하는 권한 (백그라운드 자동 동기화용) */
    val OPTIONAL: Set<String> = setOf("android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND")

    val ALL: Set<String> = REQUIRED + OPTIONAL

    /** 성공하면 화면에 보여줄 문구를 돌려준다. 실패하면 예외를 던진다. */
    suspend fun run(context: Context): String {
        val client = HealthConnectClient.getOrCreate(context)

        val today = LocalDate.now()
        val result = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    today.atStartOfDay(),
                    LocalDateTime.now()
                )
            )
        )
        val steps = result[StepsRecord.COUNT_TOTAL] ?: 0L
        val dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)   // 2026-09-25

        commit(listOf(dateStr to steps))

        val at = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        return "$dateStr\n${steps}걸음 전송 완료\n($at)"
    }

    /**
     * 지난 [days]일치를 하루 단위로 묶어서 한 번에 올린다.
     *
     * 걸음수가 0인 날은 건너뛴다. Health Connect에 기록이 없는 날까지 0으로 덮어쓰면
     * 앱에 직접 입력해둔 값이 지워지기 때문이다.
     */
    suspend fun backfill(context: Context, days: Int): String {
        val client = HealthConnectClient.getOrCreate(context)
        val start = LocalDate.now().minusDays(days.toLong())

        val groups = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(),
                    LocalDateTime.now()
                ),
                timeRangeSlicer = Period.ofDays(1)
            )
        )

        val entries = groups.mapNotNull { g ->
            val s = g.result[StepsRecord.COUNT_TOTAL] ?: 0L
            if (s <= 0L) null
            else g.startTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE) to s
        }

        if (entries.isEmpty()) {
            return "가져올 지난 기록이 없습니다.\n\n" +
                "삼성헬스의 Health Connect 연결은\n켠 시점부터 데이터를 넘깁니다."
        }

        // Firestore commit 은 한 번에 500건까지
        entries.chunked(400).forEach { commit(it) }

        return "${entries.size}일치 전송 완료\n" +
            "${entries.first().first} ~ ${entries.last().first}\n\n" +
            "FITLOG 앱을 열면 달력에 반영됩니다."
    }

    private fun commit(entries: List<Pair<String, Long>>) {
        val now = System.currentTimeMillis().toString()
        val writes = JSONArray()

        for ((dateStr, steps) in entries) {
            val id = "s-$dateStr"
            val fields = JSONObject()
                .put("id", JSONObject().put("stringValue", id))
                .put("dateStr", JSONObject().put("stringValue", dateStr))
                .put("ampm", JSONObject().put("stringValue", "am"))
                .put("type", JSONObject().put("stringValue", "steps"))
                .put("steps", JSONObject().put("integerValue", steps.toString()))
                .put("ts", JSONObject().put("integerValue", now))

            writes.put(
                JSONObject().put(
                    "update",
                    JSONObject()
                        .put("name", "projects/$PROJECT/databases/(default)/documents/$COLLECTION/$id")
                        .put("fields", fields)
                )
            )
        }

        val body = JSONObject().put("writes", writes).toString()

        val url = URL(
            "https://firestore.googleapis.com/v1/projects/$PROJECT" +
                "/databases/(default)/documents:commit?key=$API_KEY"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IllegalStateException("서버 오류 HTTP $code\n$err")
            }
        } finally {
            conn.disconnect()
        }
    }
}
