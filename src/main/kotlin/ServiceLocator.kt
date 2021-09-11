import com.squareup.moshi.Moshi

var SL = ServiceLocator()

class ServiceLocator(
    val loader: Loader = Loader(),
    val moshi: Moshi = Moshi.Builder().build()
) {
}