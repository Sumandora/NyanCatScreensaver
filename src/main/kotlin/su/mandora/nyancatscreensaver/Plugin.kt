package su.mandora.nyancatscreensaver

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.IconPresentation
import com.intellij.openapi.wm.StatusBarWidget.Multiframe
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import com.intellij.util.Consumer
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.*
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.Timer
import kotlin.math.roundToInt
import kotlin.math.sign

const val PLUGIN_NAME = "Nyan Cat Screensaver"

private val nyancats = Array(6) {
    ImageIO.read(NyanCat::class.java.getResourceAsStream("nyancats/nyan${it + 1}.png"))
}

val nyanIcons = nyancats.map {
    ImageIcon(it)
}

const val SIZE_SCALE = 10

val random = Random()

const val TARGET_FPS = 60
const val ANIMATION_SPEED = 10 // updates per second

// Let's be real, nobody wants the slow ones
const val MIN_RANDOM = 0.3f
fun weightedRandom() = (random.nextFloat() * 2.0f - 1.0f).let { it * (1.0f - MIN_RANDOM) + MIN_RANDOM * sign(it) }

class NyanCat(private val editor: Editor) : JComponent() {

    private val phyicsTimer = Timer(1000 / TARGET_FPS) {
        updatePhysics()
        repaint()
    }

    private val animationTimer = Timer(1000 / ANIMATION_SPEED) {
        currFrame++
        if(currFrame >= nyancats.size)
            currFrame = 0
    }

    private lateinit var rootPane: JComponent
    private val parent = editor.contentComponent

    private var currFrame = 0

    private lateinit var box: Rectangle
    private val speed = random.nextInt(2, 4)

    private var velocity = Pair(speed * weightedRandom(), speed * weightedRandom())

    private var flipped = velocity.first < 0
    private fun getFlipTarget() = if(flipped) 1.0f else 0.0f
    private var flipProgression = getFlipTarget()

    private fun updateBounds() {
        SwingUtilities.getAncestorOfClass(JScrollPane::class.java, parent)?.let {
            val bounds = it.bounds
            bounds.location = SwingUtilities.convertPoint(it.parent, bounds.location, rootPane)
            setBounds(bounds)
        }
    }

    fun activate() {
        animationTimer.start()
        phyicsTimer.start()
        rootPane = SwingUtilities.getRootPane(parent)?.glassPane as JComponent
        rootPane.add(this)
        updateBounds()

        val realArea = getRealArea()
        val (width, height) = getFrameSize(getCurrFrame())
        val randomOffsetX = random.nextInt(realArea.x, realArea.x + realArea.width - width)
        val randomOffsetY = random.nextInt(realArea.y, realArea.y + realArea.height - height)
        box = Rectangle(randomOffsetX, randomOffsetY, width, height)
    }

    private fun Rectangle.center(): Point {
        return Point(x + width / 2, y + height / 2)
    }

    fun deactivate() {
        animationTimer.stop()
        phyicsTimer.stop()
        rootPane.remove(this)
    }

    private fun getRealArea(): Rectangle {
        val gutterWidth = (editor as EditorEx).gutterComponentEx.width
        val area = editor.scrollingModel.visibleArea
        return Rectangle(area.x - editor.scrollingModel.horizontalScrollOffset + gutterWidth, area.y - editor.scrollingModel.verticalScrollOffset, area.width, area.height)
    }

    private fun getCurrFrame(): BufferedImage {
        return nyancats[currFrame]
    }

    private fun getFrameSize(frame: BufferedImage): Pair<Int, Int> {
        return Pair(frame.width * SIZE_SCALE, frame.height * SIZE_SCALE)
    }

    private fun updatePhysics() {
        val area = getRealArea()
        val (width, height) = getFrameSize(getCurrFrame())
        if(width >= area.width || height >= area.height) {
            return
        }

        box.x = box.x.coerceIn(area.x..area.x + area.width - width)
        box.y = box.y.coerceIn(area.y..area.y + area.height - height)

        var newBox: Rectangle
        while(true) {
            val oldCenter = box.center()
            oldCenter.translate(velocity.first.roundToInt(), velocity.second.roundToInt())

            newBox = Rectangle(oldCenter.x - width / 2, oldCenter.y - height / 2, width, height)

            var canContinue = true

            if(newBox.x < area.x || newBox.x + newBox.width > area.x + area.width) {
                velocity = Pair(velocity.first * -1, velocity.second)
                canContinue = false
            }

            if(newBox.y < area.y || newBox.y + newBox.height > area.y + area.height) {
                velocity = Pair(velocity.first, velocity.second * -1)
                canContinue = false
            }

            if(canContinue)
                break
        }

        this.box = newBox

        flipped = velocity.first < 0
        flipProgression += (getFlipTarget() - flipProgression) * 0.1f
        flipProgression = flipProgression.coerceIn(0.0f..1.0f)
    }

    override fun paintComponent(g: Graphics) {
        updateBounds()
        super.paintComponent(g)
        val box = box

        val frame = nyancats[currFrame]

        g.drawImage(frame,
            (box.x + box.width * flipProgression).roundToInt(), box.y,
            (box.width * -(flipProgression * 2.0f - 1.0f)).roundToInt(), box.height, null)
    }

}

val editors = WeakHashMap<Editor, ArrayList<NyanCat>>()

fun addNew(editor: Editor) {
    editors.computeIfAbsent(editor) { arrayListOf() }.add(NyanCat(editor).also {
        it.activate()
    })
}

fun removeAllCats() {
    editors.forEach { (_, v) ->
        for(cat in v) {
            cat.deactivate()
        }
    }
    editors.clear()
}

class SummonNewNyanCatAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        addNew(FileEditorManager.getInstance(e.project!!).selectedTextEditor!!)
    }
}


class RemoveAllNyanCatsAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        removeAllCats()
    }
}

const val WIDGET_ID = "NyanCatWidget"
class NyanCatWidget(project: Project) : EditorBasedWidget(project), Multiframe, IconPresentation {

    private var currFrame = 0
    private var leStatusBar: StatusBar? = null

    private val timer = Timer(1000 / ANIMATION_SPEED) {
        currFrame++
        if(currFrame >= nyanIcons.size)
            currFrame = 0
        leStatusBar?.updateWidget(WIDGET_ID)
    }

    init {
        timer.start()
    }

    override fun install(statusBar: StatusBar) {
        super<EditorBasedWidget>.install(statusBar)
        this.leStatusBar = statusBar
    }


    override fun ID() = WIDGET_ID

    override fun copy() = NyanCatWidget(project)

    override fun getTooltipText() = "Summon new nyan cat"

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer {
            addNew(FileEditorManager.getInstance(project).selectedTextEditor!!)
        }
    }

    override fun getIcon() = nyanIcons[currFrame]

    override fun getPresentation() = this
}

const val FACTORY_ID = "NyanCatWidgetFactory"
class NyanCatWidgetFactory : StatusBarEditorBasedWidgetFactory() {
    override fun getId() = FACTORY_ID

    override fun getDisplayName() = PLUGIN_NAME

    override fun createWidget(project: Project) = NyanCatWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
}
