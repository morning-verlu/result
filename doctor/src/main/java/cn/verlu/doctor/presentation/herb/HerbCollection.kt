package cn.verlu.doctor.presentation.herb

/** 对应 API `collection` 查询参数 */
enum class HerbCollection(val param: String, val label: String) {
    All("all", "全部"),
    Shennong("shennong", "神农本草经"),
    Other("other", "其他"),
    ;

    companion object {
        fun labelFor(apiParam: String): String =
            entries.find { it.param == apiParam }?.label ?: apiParam
    }
}
