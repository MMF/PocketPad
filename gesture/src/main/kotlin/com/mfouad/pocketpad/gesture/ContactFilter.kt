package com.mfouad.pocketpad.gesture

/**
 * Best-effort palm rejection: a contact reported as large when it lands is ignored until it
 * lifts. See [GestureConfig.largeContactSize].
 */
internal class ContactFilter {
    private val rejected = HashSet<Int>()

    /** Returns true (and starts ignoring [p]) if it is a large contact. */
    fun rejectOnDown(
        p: Pointer,
        limit: Float?,
    ): Boolean {
        val size = p.size
        if (limit == null || size == null || size < limit) return false
        rejected += p.id
        return true
    }

    /** Returns true if [id] was a rejected contact; it is forgotten now that it has lifted. */
    fun release(id: Int): Boolean = rejected.remove(id)

    fun visible(pointers: List<Pointer>): List<Pointer> =
        if (rejected.isEmpty()) {
            pointers
        } else {
            pointers.filterNot {
                it.id in
                    rejected
            }
        }

    fun clear() = rejected.clear()
}
