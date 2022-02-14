package spAnGB.cpu

fun CPU.idleMul(operand: Int) {
    var o = operand
    do {
        bus.idle()
        o = o ushr 8
    } while (o != 0)
}

fun CPU.idleSmul(operand: Int) {
    var o = operand
    var helper = 0xFFFFFFFFu.toInt()
    do {
        bus.idle()
        o = o ushr 8
        helper = helper ushr 8
    } while (o != 0 && o != helper)
}