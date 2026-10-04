package com.example.model

enum class ActionType(
    val id: String,
    val title: String,
    val description: String,
    val iconName: String
) {
    SWIPE_UP("swipe_up", "向上滑动 (下翻一屏)", "在当前屏幕向上轻扫滑动，阅读时翻至下一页", "keyboard_arrow_up"),
    SWIPE_DOWN("swipe_down", "向下滑动 (上翻一屏)", "在当前屏幕向下轻扫滑动，阅读时翻回上一页", "keyboard_arrow_down"),
    SWIPE_LEFT("swipe_left", "向左翻页 (下一页)", "横向翻页阅读器向左轻扫翻到下一页", "keyboard_arrow_left"),
    SWIPE_RIGHT("swipe_right", "向右翻页 (上一页)", "横向翻页阅读器向右轻扫翻到上一页", "keyboard_arrow_right"),
    CLICK_CENTER("click_center", "屏幕中心点击", "点击屏幕正中心，可唤出阅读菜单或确认", "touch_app"),
    BACK_KEY("back_key", "系统返回键", "执行系统级返回上一级操作", "arrow_back"),
    HOME_KEY("home_key", "主屏幕键", "返回手机桌面", "home"),
    TOGGLE_TRACKING("toggle_tracking", "暂停 / 继续识别", "暂时挂起识别，避免说话或转头时误触", "pause_circle"),
    SCROLL_DOWN_SLOW("scroll_down_slow", "微幅向下微滚", "平滑滚动一小段距离，适合连续流式阅读", "expand_more"),
    SCROLL_UP_SLOW("scroll_up_slow", "微幅向上微滚", "平滑回滚一小段距离", "expand_less"),
    NONE("none", "无动作 (仅检测显示)", "不触发任何手势模拟，仅用于测试或显示", "block")
}
