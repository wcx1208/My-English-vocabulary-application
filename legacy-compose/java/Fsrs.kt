package com.wcx.mywords

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-6 间隔重复调度器 —— 移植自官方参考实现 py-fsrs（MIT License）
 * https://github.com/open-spaced-repetition/py-fsrs
 *
 * 科学依据：艾宾浩斯遗忘曲线的现代三分量记忆模型
 *   R(t,S) = (1 + FACTOR·t/S)^DECAY   可提取性：经过 t 天后还记得的概率
 *   S        稳定性：记忆强度（R 降到 90% 所需天数）
 *   D        难度：这个词对你有多难（1~10）
 * 每次复习按评分更新 S/D，再按目标保持率 r 反解下次间隔：
 *   I(r,S) = S/FACTOR · (r^(1/DECAY) − 1)
 */
class FsrsScheduler(val desiredRetention: Double = 0.90) {

    companion object {
        // FSRS-6 官方默认 21 参数（未做个性化优化；个性化需用复习日志训练，见 README v2 路线）
        val W = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722,
            0.1666, 0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729,
            0.5425, 0.0912, 0.0658, 0.1542
        )
        val DECAY = -W[20]
        val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0
        const val DAY = 86_400_000L
        const val MINUTE = 60_000L
        const val STABILITY_MIN = 0.001
        const val STABILITY_MAX = 36_500.0
        const val MAX_INTERVAL = 36_500
        const val LEARNING = 0
        const val REVIEW = 1
        const val RELEARNING = 2

        // 学习/重学步骤（分钟）：Again 回到第一步，Good 前进一步，走完毕业进复习
        val LEARNING_STEPS = longArrayOf(1 * MINUTE, 10 * MINUTE)
        val RELEARNING_STEPS = longArrayOf(10 * MINUTE)
    }

    fun retrievability(card: MemState, now: Long): Double {
        if (card.lastReview == 0L || card.stability <= 0.0) return 0.0
        val elapsedDays = ((now - card.lastReview).coerceAtLeast(0) / DAY).toDouble()
        return (1.0 + FACTOR * elapsedDays / card.stability).pow(DECAY)
    }

    /** 由目标保持率反解间隔天数 */
    fun nextInterval(stability: Double): Int =
        ((stability / FACTOR) * (desiredRetention.pow(1.0 / DECAY) - 1.0))
            .roundToInt().coerceIn(1, MAX_INTERVAL)

    fun review(card: MemState, rating: Int, now: Long): MemState {
        // rating: 1=Again 2=Hard 3=Good 4=Easy
        require(rating in 1..4) { "评分必须是 1..4" }
        val elapsedDays = if (card.lastReview == 0L) null
        else ((now - card.lastReview).coerceAtLeast(0) / DAY).toInt()

        var state = card.state
        var step = card.step
        var s = card.stability
        var d = card.difficulty
        var lapses = card.lapses
        var due: Long

        when (state) {
            LEARNING, RELEARNING -> {
                val steps = if (state == LEARNING) LEARNING_STEPS else RELEARNING_STEPS
                if (s <= 0.0) {                      // 首次学习
                    s = initialStability(rating)
                    d = initialDifficulty(rating)
                } else if (elapsedDays != null && elapsedDays < 1) {  // 同天/分钟级复习
                    s = shortTermStability(s, rating)
                    d = nextDifficulty(d, rating)
                } else {                             // 隔天后再见，按长程公式
                    val r = if (elapsedDays == null) 0.9
                    else (1.0 + FACTOR * elapsedDays / s).pow(DECAY)
                    s = nextStability(d, s, r, rating)
                    d = nextDifficulty(d, rating)
                }
                when (rating) {
                    1 -> { step = 0; due = now + steps[0] }
                    2 -> {
                        due = now + if (steps.size > 1) (steps[0] + steps[1]) / 2 else (steps[0] * 3 / 2)
                    }
                    3 -> if (step + 1 >= steps.size) { state = REVIEW; step = 0; due = now + nextInterval(s) * DAY }
                    else { step += 1; due = now + steps[step] }
                    else -> { state = REVIEW; step = 0; due = now + nextInterval(s) * DAY }
                }
            }
            else -> {                               // REVIEW
                if (elapsedDays != null && elapsedDays < 1) {
                    s = shortTermStability(s, rating)
                } else {
                    val r = if (elapsedDays == null) 0.9
                    else (1.0 + FACTOR * elapsedDays / s).pow(DECAY)
                    s = nextStability(d, s, r, rating)
                }
                d = nextDifficulty(d, rating)
                if (rating == 1) {
                    state = RELEARNING; step = 0
                    due = now + RELEARNING_STEPS[0]
                    lapses += 1
                } else {
                    due = now + nextInterval(s) * DAY
                }
            }
        }

        return MemState(
            state = state, step = step,
            stability = s.coerceIn(STABILITY_MIN, STABILITY_MAX),
            difficulty = d.coerceIn(1.0, 10.0),
            due = due, lastReview = now,
            reps = card.reps + 1, lapses = lapses
        )
    }

    private fun initialStability(rating: Int): Double =
        W[rating - 1].coerceAtLeast(STABILITY_MIN)

    private fun initialDifficulty(rating: Int): Double =
        (W[4] - exp(W[5] * (rating - 1)) + 1.0).coerceIn(1.0, 10.0)

    private fun shortTermStability(s: Double, rating: Int): Double {
        val sinc = exp(W[17] * (rating - 3 + W[18])) * s.pow(-W[19])
        val inc = if (rating >= 3) maxOf(sinc, 1.0) else sinc
        return s * inc
    }

    private fun nextDifficulty(d: Double, rating: Int): Double {
        val deltaD = -W[6] * (rating - 3)
        val damped = deltaD * (10.0 - d) / 9.0          // 线性阻尼：越难越难变得更难
        val reverted = W[7] * initialDifficulty(4) + (1 - W[7]) * (d + damped)  // 均值回归
        return reverted.coerceIn(1.0, 10.0)
    }

    private fun nextStability(d: Double, s: Double, r: Double, rating: Int): Double =
        if (rating == 1) nextForgetStability(d, s, r) else nextRecallStability(d, s, r, rating)

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Int): Double {
        val hardPenalty = if (rating == 2) W[15] else 1.0
        val easyBonus = if (rating == 4) W[16] else 1.0
        val inc = exp(W[8]) * (11.0 - d) * s.pow(-W[9]) *
            (exp((1.0 - r) * W[10]) - 1.0) * hardPenalty * easyBonus
        return s * (1.0 + inc)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val longTerm = W[11] * d.pow(-W[12]) *
            ((s + 1.0).pow(W[13]) - 1.0) * exp((1.0 - r) * W[14])
        val shortTerm = s / exp(W[17] * W[18])
        return minOf(longTerm, shortTerm)
    }
}
