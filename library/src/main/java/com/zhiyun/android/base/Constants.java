/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhiyun.android.base;


public interface Constants {
    AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(16, 9);

    int MANUAL_WB_LOWER = 2000;
    int MANUAL_WB_UPER = 12000;

    int FACING_BACK = 0;
    int FACING_FRONT = 1;

    int FLASH_OFF = 0;
    int FLASH_ON = 1;
    int FLASH_TORCH = 2;
    int FLASH_AUTO = 3;
    int FLASH_RED_EYE = 4;

    int GRID_NONE = 0;//网格线 无
    int GRID_GRID = 1;//网格线 网格
    int GRID_GRID_AND_DIAGONAL = 2;//网格线 网格+对角线
    int GRID_CENTER_POINT = 3; //网格线 中心点
}
