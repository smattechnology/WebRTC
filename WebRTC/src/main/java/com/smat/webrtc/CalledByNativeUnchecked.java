package com.smat.webrtc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
/* loaded from: input.aar:classes.jar:org/webrtc/CalledByNativeUnchecked.class */
public @interface CalledByNativeUnchecked {
    String value() default "";
}
