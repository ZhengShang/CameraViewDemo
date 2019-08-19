package cn.zhengshang.cameraview;

import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import cn.zhengshang.base.AspectRatio;

import static org.hamcrest.CoreMatchers.either;

public class AspectRatioIsCloseTo extends TypeSafeMatcher<AspectRatio> {

    private final AspectRatio mRatio;
    private final static float ERROR = 0.01f;

    public AspectRatioIsCloseTo(AspectRatio ratio) {
        mRatio = ratio;
    }

    @Override
    protected boolean matchesSafely(AspectRatio item) {
        float other = item.toFloat();
        float self = mRatio.toFloat();
        return self - ERROR < other && other < self + ERROR;
    }

    @Override
    public void describeTo(Description description) {
        description.appendText("an aspect ratio of ").appendValue(mRatio.toString());
    }

    @Factory
    public static Matcher<AspectRatio> closeTo(AspectRatio ratio) {
        return new AspectRatioIsCloseTo(ratio);
    }

    @Factory
    public static Matcher<AspectRatio> closeToOrInverse(AspectRatio ratio) {
        return either(closeTo(ratio)).or(closeTo(ratio.inverse()));
    }

}
