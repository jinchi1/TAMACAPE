package ai.tamacape

internal enum class TurnSignal(val title: String) {
    OFF("꺼짐"),
    LEFT("왼쪽"),
    RIGHT("오른쪽"),
}

internal enum class SignalStep {
    APPROACH, TURNED, COMPLETE,
}

internal data class SignalPractice(
    val step: SignalStep = SignalStep.APPROACH,
    val signal: TurnSignal = TurnSignal.OFF,
) {
    fun select(next: TurnSignal): SignalPractice = when {
        step == SignalStep.COMPLETE -> this
        step == SignalStep.TURNED && next == TurnSignal.OFF ->
            copy(step = SignalStep.COMPLETE, signal = next)
        else -> copy(signal = next)
    }

    fun turn(): SignalPractice =
        if (step == SignalStep.APPROACH && signal == TurnSignal.RIGHT) copy(step = SignalStep.TURNED)
        else this
}
