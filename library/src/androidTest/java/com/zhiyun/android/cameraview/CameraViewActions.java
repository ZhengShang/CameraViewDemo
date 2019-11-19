package cn.zhengshang.cameraview;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;

import org.hamcrest.Matcher;

import cn.zhengshang.base.AspectRatio;

import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;

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
