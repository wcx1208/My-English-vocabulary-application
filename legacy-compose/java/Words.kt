package com.wcx.mywords

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

// 词条模型：数据来自《六级必背词汇》手册 + 不背单词 CET6 词书 + ECDICT(8.5k★)
data class Word(
    val w: String,      // 单词/搭配
    val ipa: String,    // 音标（可空）
    val meaning: String,
    val note: String,   // 搭配/考点/近义词/短语/记忆法(词根词缀)
    val tier: String,   // 兼容旧字段
    val lv: String,     // 等级: J进阶 H核心 T拓展 B基础
    val lvn: String,    // 等级名
    val cat: String,    // 分类
    val ex: String = "",     // 例句（真题例句优先）
    val exm: String = "",    // 例句翻译
    val imp: Int = 2,        // 重要度 3=★★★ 2=★★ 1=★
    val cl: Int = 0,         // 柯林斯星级 0-5
    val col: List<String> = emptyList(),  // 搭配（CO/PH 按词嵌入）
    val syn: String = "",    // 同义替换（SY 按词嵌入）
    val cf: List<Pair<String, String>> = emptyList()  // 辨析: (词, 形/义)
) : Serializable

object WordBank {
    val loaded get() = this::all.isInitialized
    lateinit var all: List<Word>
    lateinit var index: Map<String, Word>
    fun load(ctx: Context) {
        if (::all.isInitialized) return
        val txt = ctx.assets.open("words.json").bufferedReader().readText()
        val arr = JSONArray(txt)
        val list = ArrayList<Word>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cf = ArrayList<Pair<String, String>>()
            val cfa = o.optJSONArray("cf")
            if (cfa != null) for (j in 0 until cfa.length()) {
                val item = cfa.optJSONArray(j) ?: continue
                val cw = item.optString(0); val rel = item.optString(1, "形")
                if (cw.isNotEmpty()) cf.add(cw to rel)
            }
            val colA = o.optJSONArray("col")
            val colL = ArrayList<String>()
            if (colA != null) for (j in 0 until colA.length()) colL.add(colA.optString(j))
            list.add(Word(
                o.optString("w"), o.optString("i"), o.optString("m"),
                o.optString("n"), o.optString("t"), o.optString("lv", o.optString("t")),
                o.optString("lvn", ""), o.optString("c"),
                o.optString("ex"), o.optString("exm"),
                o.optInt("imp", 2), o.optInt("cl", 0), colL, o.optString("syn"), cf
            ))
        }
        all = list
        index = list.associateBy { it.w }
    }
}

/** FSRS-6 记忆状态（每个词一份，持久化为 JSON） */
data class MemState(
    val state: Int,          // 0 Learning / 1 Review / 2 Relearning
    val step: Int,           // 学习步骤索引
    val stability: Double,   // 稳定性：R 降到 90% 所需天数
    val difficulty: Double,  // 难度 1~10
    val due: Long,           // 下次到期时间（epoch ms）
    val lastReview: Long,    // 上次复习时间
    val reps: Int,           // 总复习次数
    val lapses: Int          // 遗忘（Again）次数
)

/** 进度持久化 v2：{"v":2,"cards":{word:{st,sp,s,d,due,lr,r,l}},"hist":{date:count}} */
object ProgressStore {
    private const val FILE = "progress"
    private const val DATA_KEY = "data"
    private const val CORRUPT_BACKUP_KEY = "corrupt_backup"
    private const val PRE_IMPORT_BACKUP_KEY = "pre_import_backup"
    private var readWarning: String? = null

    data class ImportPreview(
        val root: JSONObject,
        val imported: Int,
        val skippedUnknown: Int,
        val historyDays: Int,
        val includesHistory: Boolean
    )

    /** 读取并清除一次性格式损坏提示，避免每次重组都重复弹出。 */
    fun consumeReadWarning(): String? {
        val result = readWarning
        readWarning = null
        return result
    }

    /** 供设置页导出损坏前的原始进度串，避免“已备份但用户拿不到” */
    fun readCorruptBackup(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString(CORRUPT_BACKUP_KEY, null)

    fun read(ctx: Context): JSONObject {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val text = prefs.getString(DATA_KEY, "{}") ?: "{}"
        val raw = try {
            JSONObject(text)
        } catch (e: Exception) {
            // 原始字符串另存一份，后续学习也不会把唯一的恢复线索覆盖掉。
            if (text.isNotBlank() && text != "{}") {
                prefs.edit().putString(CORRUPT_BACKUP_KEY, text).apply()
            }
            readWarning = "检测到原进度数据损坏，已保留原始备份。修复前可先导出当前备份或联系开发者。"
            JSONObject().put("v", 2).put("cards", JSONObject())
        }
        if (raw.optInt("v", 1) == 2) return raw
        // ---- v1 迁移：旧格式 {word:[iv,ease,due,reps,grade]} ----
        val cards = JSONObject()
        val keys = raw.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val a = raw.optJSONArray(k) ?: continue
            val iv = a.optInt(0); val ease = a.optDouble(1); val due = a.optLong(2)
            val reps = a.optInt(3); val grade = a.optInt(4)
            if (reps <= 0) continue
            val s = maxOf(iv, 1).toDouble()
            cards.put(k, JSONObject()
                .put("st", if (iv >= 1) FsrsScheduler.REVIEW else FsrsScheduler.LEARNING)
                .put("sp", 0).put("s", s).put("d", ease.coerceIn(1.0, 10.0))
                .put("due", due).put("lr", due - (s.toLong() * FsrsScheduler.DAY))
                .put("r", reps).put("l", if (grade == 0) 1 else 0))
        }
        return JSONObject().put("v", 2).put("cards", cards)
    }

    fun write(ctx: Context, root: JSONObject) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(DATA_KEY, root.toString()).apply()
    }

    fun cardOf(root: JSONObject, word: String): MemState? {
        val c = root.optJSONObject("cards")?.optJSONObject(word) ?: return null
        return decodeCard(c)
    }

    fun putCard(root: JSONObject, word: String, c: MemState) {
        val cards = root.optJSONObject("cards") ?: JSONObject().also { root.put("cards", it) }
        cards.put(word, JSONObject()
            .put("st", c.state).put("sp", c.step).put("s", c.stability).put("d", c.difficulty)
            .put("due", c.due).put("lr", c.lastReview).put("r", c.reps).put("l", c.lapses))
    }

    /** 导出完整学习备份，包含七日统计和连续打卡所使用的 hist。 */
    fun exportBackup(root: JSONObject): JSONObject {
        val cards = JSONObject((root.optJSONObject("cards") ?: JSONObject()).toString())
        val hist = JSONObject((root.optJSONObject("hist") ?: JSONObject()).toString())
        return JSONObject()
            .put("format", "mywords-backup")
            .put("v", 2)
            .put("exportedAt", System.currentTimeMillis())
            .put("cards", cards)
            .put("hist", hist)
    }

    /**
     * 只解析和校验，不写存储。兼容旧版“纯 cards 对象”，全部通过后才允许覆盖导入。
     * 未知单词会跳过并计数；已知单词的非法状态会使整个导入失败，避免静默丢数据。
     */
    fun parseImport(text: String): ImportPreview {
        if (text.isBlank()) throw IllegalArgumentException("请先粘贴备份 JSON")
        val input = try { JSONObject(text) } catch (e: Exception) {
            throw IllegalArgumentException("JSON 格式不正确：${e.message}")
        }
        val cards = if (input.has("cards")) {
            input.optJSONObject("cards") ?: throw IllegalArgumentException("cards 必须是对象")
        } else input
        val hist = if (input.has("hist")) {
            input.optJSONObject("hist") ?: throw IllegalArgumentException("hist 必须是对象")
        } else JSONObject()

        val root = JSONObject().put("v", 2).put("cards", JSONObject())
        var imported = 0
        var skippedUnknown = 0
        val keys = cards.keys()
        while (keys.hasNext()) {
            val word = keys.next()
            if (WordBank.loaded && !WordBank.index.containsKey(word)) {
                skippedUnknown++
                continue
            }
            val obj = cards.optJSONObject(word)
                ?: throw IllegalArgumentException("“$word”的卡片数据不是对象")
            val card = decodeCard(obj)
                ?: throw IllegalArgumentException("“$word”的卡片字段或学习状态无效")
            putCard(root, word, card)
            imported++
        }

        val cleanHist = JSONObject()
        val histKeys = hist.keys()
        while (histKeys.hasNext()) {
            val key = histKeys.next()
            val value = hist.opt(key)
            val count = hist.optLong(key, -1)
            if (!key.matches(Regex("\\d{4}-\\d{1,2}-\\d{1,2}")) || value !is Number || count < 0) {
                throw IllegalArgumentException("学习历史中的“$key”无效")
            }
            cleanHist.put(key, count)
        }
        if (input.has("hist")) root.put("hist", cleanHist)
        return ImportPreview(root, imported, skippedUnknown, cleanHist.length(), input.has("hist"))
    }

    /** 用户确认后一次性替换进度，并在同一偏好文件中保留导入前版本。 */
    fun replaceProgress(ctx: Context, root: JSONObject) {
        val prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(DATA_KEY, root.toString())
        val previous = prefs.getString(DATA_KEY, null)
        if (!previous.isNullOrBlank()) editor.putString(PRE_IMPORT_BACKUP_KEY, previous)
        editor.apply()
    }

    /** 将持久化卡片安全转换为内存模型；旧版遗留的非法卡片按未学习处理，防止崩溃。 */
    private fun decodeCard(c: JSONObject): MemState? {
        val required = listOf("st", "sp", "s", "d", "due", "lr", "r", "l")
        if (required.any { !c.has(it) }) return null
        val state = c.optInt("st", -1)
        val step = c.optInt("sp", -1)
        val stability = c.optDouble("s", Double.NaN)
        val difficulty = c.optDouble("d", Double.NaN)
        val due = c.optLong("due", -1)
        val lastReview = c.optLong("lr", -1)
        val reps = c.optInt("r", -1)
        val lapses = c.optInt("l", -1)
        if (state !in FsrsScheduler.LEARNING..FsrsScheduler.RELEARNING) return null
        val validStep = when (state) {
            FsrsScheduler.LEARNING -> step in FsrsScheduler.LEARNING_STEPS.indices
            else -> step == 0
        }
        if (!validStep || !stability.isFinite() || !difficulty.isFinite()) return null
        if (stability < FsrsScheduler.STABILITY_MIN || stability > FsrsScheduler.STABILITY_MAX) return null
        if (difficulty !in 1.0..10.0 || due < 0 || lastReview < 0) return null
        if (reps <= 0 || lapses < 0 || reps > 10_000_000 || lapses > 10_000_000) return null
        val now = System.currentTimeMillis()
        if (due > now + FsrsScheduler.DAY * 40_000L || lastReview > now + FsrsScheduler.DAY * 365L) return null
        return MemState(state, step, stability, difficulty, due, lastReview, reps, lapses)
    }
}
