package cn.zhengshang.base;

import android.graphics.Rect;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Created by shangzheng on 2019-09-19.
 *            🐳🐳🐳🍒           14:26 🥥
 */
public class Face {
    private final int id;
    private final Rect rect;

    private Face(int id, Rect rect) {
        this.id = id;
        this.rect = rect;
    }

    public static Face valueOf(int id, Rect rect) {
        return new Face(id, rect);
    }

    public int getId() {
        return id;
    }

    public Rect getRect() {
        return rect;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, rect);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Face face = (Face) o;
        return id == face.id &&
                rect.equals(face.rect);
    }

    @NonNull
    @Override
    public String toString() {
        return "Face{" +
                "id=" + id +
                ", rect=" + rect +
                '}';
    }
}
