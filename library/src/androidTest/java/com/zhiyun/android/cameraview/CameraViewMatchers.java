package com.zhiyun.android.cameraview;

import android.support.annotation.NonNull;
import android.view.View;

import com.zhiyun.android.base.AspectRatio;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

class CameraViewMatchers {

    static Matcher<View> hasAspectRatio(@NonNull final AspectRatio ratio) {
        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("has aspect ratio of " + ratio);
            }

            @Override
            protected boolean matchesSafely(View view) {
                return ratio.equals(((CameraView) view).getAspectRatio());
            }
        };
    }

}
