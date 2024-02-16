module kevent {
    //kotlin
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core;
    //logging
    requires io.github.oshai.kotlinlogging;

    exports org.rationalityfrontline.kevent;
}