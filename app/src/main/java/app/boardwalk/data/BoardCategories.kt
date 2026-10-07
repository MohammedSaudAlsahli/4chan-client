package app.boardwalk.data

data class BoardGroup(val name: String, val boards: List<Board>)

/** Homepage grouping; the live boards API remains the authority for available boards and titles. */
object BoardCategories {
    private val sections = listOf(
        "Japanese Culture" to "a c w m cgl cm f n jp vt",
        "Video Games" to "v vg vm vmg vp vr vrpg vst",
        "Interests" to "co g tv k o an tg sp xs pw sci his int out toy",
        "Creative" to "i po p ck ic wg lit mu fa 3 gd diy wsg qst",
        "Other" to "biz trv fit x adv lgbt mlp news wsr vip",
        "Misc." to "b r9k pol bant soc s4s",
        "Adult" to "s hc hm h e u d y t hr gif aco",
    ).map { (name, ids) -> name to ids.split(' ') }

    fun group(available: List<Board>): List<BoardGroup> {
        val byId = available.associateBy { it.id }
        val known = sections.flatMap { it.second }.toSet()
        val result = sections.mapNotNull { (name, ids) ->
            ids.mapNotNull(byId::get).takeIf { it.isNotEmpty() }?.let { BoardGroup(name, it) }
        }
        val newBoards = available.filter { it.id !in known }
        return if (newBoards.isEmpty()) result else result + BoardGroup("New boards", newBoards)
    }
}
