package com.affinetablet.client

import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/**
 * A WebView that keeps a resting palm from generating touches while the S Pen is in use,
 * so AFFiNE's edgeless/whiteboard canvas only ever sees the pen's pointer input.
 *
 * The web platform's Pointer Events API has no concept of "this touch is a palm" - browsers
 * only get raw pointers with a type of pen/touch/mouse. Real palm rejection has to happen
 * below that, at the point where we still have Android's per-pointer MotionEvent.getToolType(),
 * so we intercept touch dispatch here and strip finger pointers out of the event stream
 * whenever a stylus is (or was just) in play. Two Android touch feeds get read for this:
 *
 *  - dispatchGenericMotionEvent(): S Pen hover (ACTION_HOVER_*). Samsung's S Pen reports
 *    proximity above the screen before it ever touches down, which lets us mark the stylus
 *    "active" before a resting palm's touch-down arrives - avoiding the common failure mode
 *    where the palm's first touch sneaks through because it landed a few ms before the pen.
 *  - dispatchTouchEvent(): actual contact. Any pointer here whose tool type isn't
 *    TOOL_TYPE_STYLUS/TOOL_TYPE_ERASER is dropped while the stylus is active; if that leaves
 *    zero pointers the whole event is swallowed, otherwise a filtered MotionEvent containing
 *    only the surviving pointer(s) is forwarded on.
 *
 * Pressure/tilt data for the surviving stylus pointer is untouched, so Chromium's own
 * PointerEvent synthesis (pressure, tiltX/tiltY) still works normally for AFFiNE's pen tool.
 */
class PalmRejectingWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : WebView(context, attrs, defStyleAttr) {

    private var stylusActiveUntil = 0L

    /** Grace period after the last stylus signal during which finger touches are still rejected.
     *  Covers the gap between lifting the pen and a resting palm finally leaving the glass. */
    private val stylusGraceMs = 400L

    private fun markStylusActive() {
        stylusActiveUntil = SystemClock.uptimeMillis() + stylusGraceMs
    }

    private fun isStylusActive(now: Long) = now < stylusActiveUntil

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if ((action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) &&
            isStylusTool(event.getToolType(0))
        ) {
            markStylusActive()
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val now = SystemClock.uptimeMillis()

        for (i in 0 until event.pointerCount) {
            if (isStylusTool(event.getToolType(i))) {
                markStylusActive()
                break
            }
        }

        if (!isStylusActive(now)) {
            return super.dispatchTouchEvent(event)
        }

        val keepIndices = (0 until event.pointerCount).filter { isStylusTool(event.getToolType(it)) }

        if (keepIndices.size == event.pointerCount) {
            return super.dispatchTouchEvent(event)
        }
        if (keepIndices.isEmpty()) {
            // Purely finger/palm contact while the pen is active: swallow it entirely.
            return true
        }

        val filtered = buildFilteredEvent(event, keepIndices) ?: return true
        return try {
            super.dispatchTouchEvent(filtered)
        } finally {
            filtered.recycle()
        }
    }

    private fun isStylusTool(toolType: Int) =
        toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

    /** Rebuilds [src] with only the pointers at [keep], remapping DOWN/UP actions that
     *  referenced a now-dropped pointer index down to a plain MOVE. */
    private fun buildFilteredEvent(src: MotionEvent, keep: List<Int>): MotionEvent? {
        val count = keep.size
        val props = Array(count) { MotionEvent.PointerProperties() }
        val coords = Array(count) { MotionEvent.PointerCoords() }
        for ((newIndex, origIndex) in keep.withIndex()) {
            src.getPointerProperties(origIndex, props[newIndex])
            src.getPointerCoords(origIndex, coords[newIndex])
        }

        val origAction = src.actionMasked
        val newAction = when (origAction) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> {
                val remappedIndex = keep.indexOf(src.actionIndex)
                when {
                    remappedIndex == -1 -> MotionEvent.ACTION_MOVE
                    origAction == MotionEvent.ACTION_POINTER_DOWN ->
                        MotionEvent.ACTION_POINTER_DOWN or (remappedIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                    else ->
                        MotionEvent.ACTION_POINTER_UP or (remappedIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
                }
            }
            else -> origAction
        }

        return runCatching {
            MotionEvent.obtain(
                src.downTime,
                src.eventTime,
                newAction,
                count,
                props,
                coords,
                src.metaState,
                src.buttonState,
                src.xPrecision,
                src.yPrecision,
                src.deviceId,
                src.edgeFlags,
                src.source,
                src.flags,
            )
        }.getOrNull()
    }
}
