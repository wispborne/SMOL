package utilities

sealed class SmolResult<S, F> {
    class Success<S, F>(val value: S) : SmolResult<S, F>()
    class Failure<S, F>(val value: F) : SmolResult<S, F>()

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure


    val success: S?
        get() = if (this is Success) this.value else null

    val failure: F?
        get() = if (this is Failure) this.value else null
}