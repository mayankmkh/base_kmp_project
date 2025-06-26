package dev.mayankmkh.basekmpproject.shared.libs.arch.core.data

class NoDataException(message: String? = NO_SUCH_DATA) : NoSuchElementException(message) {
    companion object {
        const val NO_SUCH_DATA = "Data not found in the database"
    }
}
