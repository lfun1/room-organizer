package com.lbunny.roomorganizer.ui.components

// =============================================================================
// DragAndDrop.kt  —  long-press a card and drop it onto a folder to move it
// =============================================================================
//
// Android has a built-in drag-and-drop system. There are three parts here:
//
//   1. START A DRAG   (every card, on long-press)
//      rememberStartNodeDrag() → calls Android's View.startDragAndDrop(...).
//      The drag "carries" a small package of data: the node's id. Android
//      draws a floating "shadow" (a blue name tag, see NodeDragShadow) that
//      follows your finger.
//
//   2. DROP TARGETS   (folder cards and breadcrumb crumbs)
//      NodeDropTargetState + Modifier.nodeDropTarget(...) → highlights while
//      the shadow hovers over it, and receives the id when you let go.
//
//   3. DRAG MONITOR   (the whole grid)
//      DragMonitorState → remembers WHICH card is being dragged, so that card
//      can fade out while it's "picked up", and un-fade when the drag ends.
//
// The package travels as `ClipData` (the same format copy/paste uses) with
// the id as plain text. We ALSO attach the id as `localState`, a shortcut that
// works because the drag starts and ends inside our own app.
//
// WHY START THE DRAG OURSELVES? (bug fix)
//   The first version used Compose's ready-made `Modifier.dragAndDropSource`.
//   It watches for a long-press on its own, but the card's tap/double-tap
//   handler (`combinedClickable`) claimed the same touch first, so the
//   long-press was never noticed and no drag started. Now the card has ONE
//   gesture handler for tap, double tap, AND long-press, and the long-press
//   calls rememberStartNodeDrag() below.
// =============================================================================

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/** A label for our drag data, so we can recognize it. */
private const val NODE_DRAG_LABEL = "room-organizer-node"

/** Reads the dragged node's id out of a drag event (or null if it isn't ours). */
private fun DragAndDropEvent.draggedNodeId(): Long? {
    val dragEvent = toAndroidDragEvent()
    // Try the localState shortcut first, then the ClipData text.
    return (dragEvent.localState as? Long)
        ?: dragEvent.clipData?.getItemAt(0)?.text?.toString()?.toLongOrNull()
}

/** Only react to drags that carry plain text (our node ids). */
private fun DragAndDropEvent.isNodeDrag(): Boolean =
    mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)

// =============================================================================
// 1. Starting a drag
// =============================================================================

/**
 * Returns a function that starts dragging a node. Call it from a long-press.
 *
 *   val startDrag = rememberStartNodeDrag()
 *   ... onLongClick = { startDrag(node.id, node.name) }
 *
 * `LocalView.current` = the Android View that hosts all our Compose UI.
 * Android's drag system works on Views, so we start the drag from it; the
 * Compose drop targets then receive the drag events automatically.
 */
@Composable
fun rememberStartNodeDrag(): (nodeId: Long, label: String) -> Unit {
    val view = LocalView.current
    val haptics = LocalHapticFeedback.current
    return remember(view, haptics) {
        { nodeId, label ->
            // A short vibration so you can FEEL that the card was picked up.
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            view.startDragAndDrop(
                ClipData.newPlainText(NODE_DRAG_LABEL, nodeId.toString()),
                NodeDragShadow(view.context, label),  // what follows your finger
                nodeId,                               // localState shortcut
                0,                                    // flags: 0 = only within our app
            )
        }
    }
}

/**
 * The floating "shadow" that follows your finger while dragging: a
 * semi-transparent blue rounded tag with the node's name, e.g. [ Closet ].
 *
 * A DragShadowBuilder answers two questions for Android:
 *   onProvideShadowMetrics → how big is the shadow, and where is the finger on it?
 *   onDrawShadow           → draw it (on a plain Android Canvas)
 */
private class NodeDragShadow(context: Context, private val label: String) : View.DragShadowBuilder() {

    private val metrics = context.resources.displayMetrics
    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics)
    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics)

    // Paint = the "brush" settings for drawing. ANTI_ALIAS = smooth edges.
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD0B57D0.toInt()   // Drive blue, ~87% opaque (DD) so it looks "lifted"
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()   // white text
        textSize = sp(16f)
        isFakeBoldText = true
    }

    private val padH = dp(16f)
    private val padV = dp(10f)
    // Limit very long names so the tag doesn't cover the whole screen.
    private val text = if (label.length > 28) label.take(27) + "…" else label
    private val width = (textPaint.measureText(text) + padH * 2).toInt()
    private val height = (textPaint.fontMetrics.let { it.descent - it.ascent } + padV * 2).toInt()

    override fun onProvideShadowMetrics(outShadowSize: Point, outShadowTouchPoint: Point) {
        outShadowSize.set(width, height)
        // Put the finger in the middle of the tag.
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        val radius = height / 2f   // fully rounded "pill" ends
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, backgroundPaint)
        // drawText's y is the text BASELINE, so shift down by the font's ascent.
        canvas.drawText(text, padH, padV - textPaint.fontMetrics.ascent, textPaint)
    }
}

// =============================================================================
// 2. Drop targets
// =============================================================================

/**
 * Receives drops. Also remembers whether something is currently hovering
 * over it ([isHovered]) so the screen can highlight the target.
 *
 * `mutableStateOf` = a value Compose watches; when it changes, the UI that
 * reads it is redrawn automatically (that's how the highlight appears).
 */
class NodeDropTargetState(private val onDropNode: (nodeId: Long) -> Unit) : DragAndDropTarget {

    var isHovered by mutableStateOf(false)
        private set

    override fun onEntered(event: DragAndDropEvent) { isHovered = true }
    override fun onExited(event: DragAndDropEvent) { isHovered = false }
    override fun onEnded(event: DragAndDropEvent) { isHovered = false }

    /** Called when the user lets go over this target. Return true = "accepted". */
    override fun onDrop(event: DragAndDropEvent): Boolean {
        isHovered = false
        val nodeId = event.draggedNodeId() ?: return false
        onDropNode(nodeId)
        return true
    }
}

/**
 * Creates (once) and remembers a [NodeDropTargetState].
 *
 * `rememberUpdatedState` keeps the LATEST onDropNode function, even though the
 * target object itself is only created once. Without it, the target would
 * keep calling an old function holding outdated screen data.
 */
@Composable
fun rememberNodeDropTarget(onDropNode: (nodeId: Long) -> Unit): NodeDropTargetState {
    val latestOnDrop by rememberUpdatedState(onDropNode)
    return remember { NodeDropTargetState { id -> latestOnDrop(id) } }
}

/**
 * Makes this UI element accept dropped nodes.
 * @param enabled false = ignore drops (e.g. items can't contain things).
 */
fun Modifier.nodeDropTarget(state: NodeDropTargetState, enabled: Boolean = true): Modifier =
    if (!enabled) this
    else dragAndDropTarget(shouldStartDragAndDrop = { it.isNodeDrag() }, target = state)

// =============================================================================
// 3. Drag monitor (which card is being dragged right now?)
// =============================================================================

/**
 * Put on a container around the grid. It doesn't accept drops itself; it just
 * watches drags start and end so the dragged card can fade out meanwhile.
 *
 *   onStarted → a drag began anywhere: remember its node id
 *   onEnded   → the drag finished (dropped or cancelled): forget it
 */
class DragMonitorState : DragAndDropTarget {
    var draggingNodeId by mutableStateOf<Long?>(null)
        private set

    override fun onStarted(event: DragAndDropEvent) { draggingNodeId = event.draggedNodeId() }
    override fun onEnded(event: DragAndDropEvent) { draggingNodeId = null }

    /** Dropping on empty space does nothing ("not accepted"). */
    override fun onDrop(event: DragAndDropEvent): Boolean = false
}

@Composable
fun rememberDragMonitor(): DragMonitorState = remember { DragMonitorState() }

fun Modifier.nodeDragMonitor(state: DragMonitorState): Modifier =
    dragAndDropTarget(shouldStartDragAndDrop = { it.isNodeDrag() }, target = state)
