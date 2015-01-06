package net.oschina.app.widget;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.view.MotionEvent;

/**
 * 基于google的可上下拉控件的改版，重写了触摸屏判断逻辑，方便实现嵌入容器View中
 * 
 * @author kymjs (kymjs123@gmail.com)
 * 
 */
public class KJSwipeRefreshLayout extends SwipeRefreshLayout {

    public KJSwipeRefreshLayout(Context context) {
        super(context);
    }

    public KJSwipeRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent arg0) {
        if (!isEnabled()) {
            return false;
        }
        return super.onTouchEvent(arg0);
    }
}