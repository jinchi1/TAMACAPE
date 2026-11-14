package ai.tamacape

internal data class Puzzle(
    val prompt: String,
    val options: List<String>,
    val answer: List<Int>,
    val hint: String,
    val explanation: String,
    val ordered: Boolean = false,
    val alleyScene: Boolean = false,
    val garageScene: Boolean = false,
    val weatherScene: Boolean = false,
)

internal data class Stage(val name: String, val intro: String, val puzzles: List<Puzzle>)

private fun choice(
    prompt: String, correct: Int, hint: String, explanation: String, vararg options: String,
) = Puzzle(prompt, options.toList(), listOf(correct), hint, explanation)

internal val stages = listOf(
    Stage("잠겨버린 차고", "모비가 별빛 차고의 첫 번째 열쇠를 찾고 있어요.", listOf(
        Puzzle("엄마·아빠와 출발 준비를 해요! 무엇부터 하면 좋을까요?",
            listOf("주변을 살피고 출발", "좌석과 거울 조정", "안전벨트 착용"),
            listOf(1, 2, 0), "출발 전에 운전 자세를 먼저 맞춰요.",
            "좌석과 거울을 조정하고 안전벨트를 맨 뒤 주변을 확인해요.", ordered = true),
        Puzzle("모비 차의 문이 열려 있어요! 출발 전에 어디를 확인할까요?",
            listOf("안전벨트", "도어", "경고등"),
            listOf(1), "계기판에 도어 열림 표시가 있어요.",
            "차가 멈춰 있을 때 모든 도어가 제대로 닫혔는지 확인해요.", garageScene = true),
    )),
    Stage("안개 속의 빛", "안개 너머의 길을 찾을 수 있게 도와주세요.", listOf(
        Puzzle("안개 너머 길을 열어 주세요. 가상 차량을 감속하고 앞차와의 간격을 확보해요.",
            listOf("감속", "간격 확보"), listOf(0, 1),
            "속도와 차간거리를 모두 조절하면 안전하게 준비할 수 있어요.",
            "안개에서는 보이는 거리에 맞춰 속도를 낮추고 앞차와의 간격을 충분히 확보해요.",
            weatherScene = true),
        choice("빗길에서 앞차가 속도를 줄여요.", 2, "젖은 노면에서는 멈추는 데 여유가 필요해요.",
            "가속 페달에서 발을 떼고 상황에 맞게 부드럽게 감속해요.",
            "급하게 추월", "경적을 울리며 접근", "감속하며 간격 확보"),
    )),
    Stage("멈춰버린 신호 도시", "골목을 살피고 모비와 함께 도시를 지나가요.", listOf(
        choice("횡단보도에서 사람이 건너고 있어요.", 1, "먼저 길을 쓰는 사람을 살펴요.",
            "보행자가 모두 건널 때까지 기다려요. 방향지시등을 켜고 주변을 확인한 뒤 회전하고, 회전 후 신호를 꺼요.",
            "빈 쪽으로 지나가기", "멈추고 기다리기", "경적으로 재촉하기"),
        Puzzle("골목에서 시야를 가리는 곳을 모두 골라 주세요.",
            listOf("주차된 승합차 뒤", "훤히 보이는 빈 광장", "담벼락 모퉁이"),
            listOf(0, 2), "아이 키에서 가려질 수 있는 곳을 찾아요.",
            "차량과 모퉁이 뒤에 보행자가 가려질 수 있어요. 시야가 확보될 때까지 천천히 접근해요.", alleyScene = true),
    )),
    Stage("고장 난 기계실", "계기판에 뜬 신호를 읽어 기계실을 점검해요.", listOf(
        choice("자동차가 타이어를 살펴 달래요! 어른은 어떻게 하면 좋을까요?", 2, "차량 상태를 확인할 장소를 찾아요.",
            "안전한 곳에 정차한 뒤 공기압과 타이어 상태를 확인하고 차량 설명서의 안내를 따라요.",
            "속도를 높여서 확인", "경고 표시만 지우기", "안전한 곳에 정차해 점검"),
        choice("처음 보는 불빛이 켜졌어요! 엄마·아빠와 어디에서 뜻을 찾아볼까요?", 0, "차량마다 표시와 절차가 다를 수 있어요.",
            "안전하게 정차한 상태에서 해당 차량의 사용 설명서를 확인해요.",
            "차량 사용 설명서", "경고등의 모양만 보고 추측", "다른 차의 계기판"),
    )),
    Stage("마지막 안전의 성", "그동안 익힌 습관으로 마지막 문을 열어 주세요.", listOf(
        Puzzle("비가 오는 날 출발 전 점검을 모두 골라 주세요.",
            listOf("안전벨트 확인", "출발 후 거울 조정", "시야를 가리는 물기 제거"),
            listOf(0, 2), "차가 움직이기 전에 끝낼 수 있는 일을 골라요.",
            "시야를 확보하고 안전벨트를 확인한 뒤 출발해요."),
        choice("안개 속에 횡단보도가 보여요! 어떻게 지나가야 안전할까요?", 1, "안개와 보행자 위험을 함께 생각해요.",
            "미리 감속해 보행자를 살피고 필요할 때 멈출 수 있도록 준비해요.",
            "앞차가 통과하면 따라가기", "미리 감속해 주변 확인", "횡단보도에서 가속"),
    )),
)

internal val costumes = listOf("안전모", "고글", "캡모자", "탐험모자", "헤드셋",
    "별 선글라스", "스카프", "탐험 가방", "별 망토", "안전벨트 배지")
internal val furniture = listOf("별빛 조명", "민트 러그", "탐험 책장", "구름 쿠션")

internal val familyQuestions = listOf(
    listOf("우리 차에서는 출발 전에 어떤 준비를 함께 할까요?", "문이 열려 있으면 왜 어른에게 알려야 할까요?"),
    listOf("안개 속에서는 왜 앞차와 떨어져 가야 할까요?", "빗길에서는 왜 천천히 멈춰야 할까요?"),
    listOf("길을 건너는 친구를 기다리면 어떤 마음이 들까요?", "차 뒤에서 사람이 나올 수 있다면 어떻게 해야 할까요?"),
    listOf("자동차가 보내는 신호를 무시하면 어떤 일이 생길까요?", "모르는 표시를 보면 왜 어른과 설명서를 찾아봐야 할까요?"),
    listOf("비 오는 날 우리 가족이 챙길 것을 하나씩 말해 볼까요?", "오늘 함께 배운 약속 중 하나를 골라 말해 볼까요?"),
)
