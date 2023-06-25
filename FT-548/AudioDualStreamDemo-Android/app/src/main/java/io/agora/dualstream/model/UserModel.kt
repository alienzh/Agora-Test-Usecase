package io.agora.dualstream.model

data class UserModel constructor(
    val userId: Int,
    var lowStream: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (other is UserModel) {
            return other.userId == userId
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = userId
        result = 31 * result + lowStream.hashCode()
        return result
    }
}
