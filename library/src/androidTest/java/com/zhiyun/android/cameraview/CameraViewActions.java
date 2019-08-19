package cn.zhengshang.cameraview;

import android.support.annotation.NonNull;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.view.View;

import org.hamcrest.Matcher;

import cn.zhengshang.base.AspectRatio;

import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;

class CameraViewActions {

    static ViewAction setAspectRatio(@NonNull final AspectRatio ratio) {
        return new ViewAction() {

            @Override
            public Matcher<View> getConstraints() {
                return isAssignableFrom(CameraView.class);
            }

            @Override
            public String getDescription() {
                return "Set aspect ratio to " + ratio;
            }

            @Override
            public void perform(UiController controller, View view) {
                ((CameraView) view).setAspectRatio(ratio);
            }
        };
    }

}
