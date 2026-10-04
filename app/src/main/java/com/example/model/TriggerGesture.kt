package com.example.model

enum class GestureCategory(val label: String) {
    HEAD("头部姿态"),
    FACIAL("面部动作"),
    GAZE("眼动注视"),
    HAND("手势控制")
}

enum class TriggerGesture(
    val id: String,
    val displayName: String,
    val category: GestureCategory,
    val iconName: String,
    val description: String
) {
    // 头部动作
    HEAD_PITCH_UP("head_pitch_up", "抬头 (仰头)", GestureCategory.HEAD, "arrow_upward", "向上仰起头部超过指定角度"),
    HEAD_PITCH_DOWN("head_pitch_down", "低头", GestureCategory.HEAD, "arrow_downward", "向下低头超过指定角度"),
    HEAD_YAW_LEFT("head_yaw_left", "向左转头", GestureCategory.HEAD, "arrow_back", "头部向左旋转"),
    HEAD_YAW_RIGHT("head_yaw_right", "向右转头", GestureCategory.HEAD, "arrow_forward", "头部向右旋转"),
    HEAD_ROLL_LEFT("head_roll_left", "向左歪头", GestureCategory.HEAD, "rotate_left", "头部向左侧歪斜倾斜"),
    HEAD_ROLL_RIGHT("head_roll_right", "向右歪头", GestureCategory.HEAD, "rotate_right", "头部向右侧歪斜倾斜"),

    // 面部动作
    BLINK_LEFT("blink_left", "左眼眨眼", GestureCategory.FACIAL, "visibility_off", "单左眼眨眼或长闭"),
    BLINK_RIGHT("blink_right", "右眼眨眼", GestureCategory.FACIAL, "visibility_off", "单右眼眨眼或长闭"),
    BLINK_BOTH("blink_both", "双眼长闭", GestureCategory.FACIAL, "remove_red_eye", "双眼同时闭合保持片刻"),
    MOUTH_OPEN("mouth_open", "张嘴", GestureCategory.FACIAL, "sentiment_satisfied", "张开嘴巴超过阈值"),
    SMILE("smile", "微笑", GestureCategory.FACIAL, "mood", "嘴角上扬露出微笑"),

    // 眼动注视 (Gaze Tracking)
    GAZE_LOOK_RIGHT("gaze_look_right", "视线看右边缘 (翻下一页)", GestureCategory.GAZE, "visibility", "眼球向右注视超过偏转阈值"),
    GAZE_LOOK_LEFT("gaze_look_left", "视线看左边缘 (翻上一页)", GestureCategory.GAZE, "visibility", "眼球向左注视超过偏转阈值"),
    GAZE_LOOK_UP("gaze_look_up", "视线看上边缘 (向上滚动/翻回)", GestureCategory.GAZE, "arrow_upward", "目光向上注视至页面顶部触发上翻"),
    GAZE_LOOK_DOWN("gaze_look_down", "视线看屏幕底端 (读完下翻)", GestureCategory.GAZE, "arrow_downward", "视线向下注视至页面底部触发下翻"),
    GAZE_DWELL_CORNER("gaze_dwell_corner", "右下角视线驻留触发", GestureCategory.GAZE, "hourglass_bottom", "目光在屏幕右下角翻页热区驻留达到指定时长"),

    // 手势识别
    HAND_PALM("hand_palm", "手掌张开 (举掌)", GestureCategory.HAND, "pan_tool", "向镜头展示平展的手掌"),
    HAND_FIST("hand_fist", "握拳", GestureCategory.HAND, "sports_mma", "手部收拢紧握成拳"),
    HAND_POINT_UP("hand_point_up", "向上指 / 向上挥", GestureCategory.HAND, "north", "食指指向上方或手部上挥"),
    HAND_POINT_DOWN("hand_point_down", "向下指 / 向下挥", GestureCategory.HAND, "south", "食指指向下方或手部下挥"),
    HAND_SWIPE_LEFT("hand_swipe_left", "向左挥手", GestureCategory.HAND, "west", "在镜头前从右往左轻挥手掌"),
    HAND_SWIPE_RIGHT("hand_swipe_right", "向右挥手", GestureCategory.HAND, "east", "在镜头前从左往右轻挥手掌"),
    HAND_THUMBS_UP("hand_thumbs_up", "竖大拇指 (点赞)", GestureCategory.HAND, "thumb_up", "单手竖起大拇指点赞"),
    HAND_V_SIGN("hand_v_sign", "胜利V手势 / 剪刀手", GestureCategory.HAND, "waving_hand", "伸出食指和中指呈V字")
}
