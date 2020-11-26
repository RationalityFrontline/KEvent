module kevent {
    //kotlin
    requires kotlin.stdlib;
    requires kotlinx.coroutines.core.jvm;
    //logging
    requires kotlin.logging.jvm;

    exports org.rationalityfrontline.kevent;
}