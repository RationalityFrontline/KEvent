module kevent {
    //kotlin
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    //logging
    requires io.github.microutils.kotlinlogging;

    exports org.rationalityfrontline.kevent;
}