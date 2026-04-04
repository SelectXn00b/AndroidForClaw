package ai.openclaw.app.avatar

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.live2d.sdk.cubism.framework.CubismFramework
import com.live2d.sdk.cubism.framework.ICubismLoadFileFunction
import com.live2d.sdk.cubism.framework.math.CubismMatrix44
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "Live2dGL"
private const val MODEL_DIR = "Live2D/Hiyori/"
private const val MODEL_JSON = "Hiyori.model3.json"
private const val TARGET_FPS = 30L
private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS

class Live2dGLSurfaceView(context: Context) : GLSurfaceView(context) {

    private val renderer = Live2dRenderer(context)
    private val renderHandler = Handler(Looper.getMainLooper())
    private val renderTick = object : Runnable {
        override fun run() {
            requestRender()
            renderHandler.postDelayed(this, FRAME_INTERVAL_MS)
        }
    }

    init {
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderHandler.post(renderTick)
    }

    override fun onDetachedFromWindow() {
        renderHandler.removeCallbacks(renderTick)
        super.onDetachedFromWindow()
    }

    fun postOnGLThread(block: (Live2dModel?) -> Unit) {
        queueEvent { block(renderer.model) }
    }

    fun releaseModelOnGLThread(onDone: Runnable) {
        queueEvent {
            renderer.model?.release()
            renderer.model = null
            onDone.run()
        }
    }
}

private class Live2dRenderer(private val context: Context) : GLSurfaceView.Renderer {

    @Volatile var model: Live2dModel? = null

    private var viewWidth = 0
    private var viewHeight = 0
    private var lastTime = System.nanoTime()
    private var frameworkInitialized = false
    private var frameCount = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // GL context lost — force model reload
        model = null
        initFramework()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        GLES20.glViewport(0, 0, width, height)

        if (model == null && frameworkInitialized) {
            loadModel()
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val m = model ?: return

        val now = System.nanoTime()
        val delta = ((now - lastTime) / 1_000_000_000f).coerceIn(0f, 0.1f)
        lastTime = now

        val isPaused = AvatarStateHolder.paused.value
        if (!isPaused) {
            m.lipSyncValue = AvatarStateHolder.mouthOpen.value
            m.update(delta)
            // Snapshot params every ~0.5s for body tool status reads
            if (++frameCount >= 15) {
                frameCount = 0
                AvatarStateHolder.updateCurrentParams(m.snapshotParameters())
            }
        }

        // Build projection — half-body crop, aspect-correct
        val projection = CubismMatrix44.create()
        m.modelMatrix.setWidth(2.0f)

        // Aspect correction: keep model proportions in non-square viewport
        val aspect = viewWidth.toFloat() / viewHeight.toFloat()
        if (aspect < 1f) {
            projection.scale(1f, aspect)
        } else {
            projection.scale(1f / aspect, 1f)
        }

        // Half-body crop: show head to waist
        // Window 180x150dp → aspect 1.2 → X shrunk by 1/1.2
        // Scale 1.3 → Y visible range ≈ 1.54 model units (enough for head-to-waist)
        // Translate -0.3 → shifts view up so head top (Y≈0.9) stays in frame
        projection.scale(1.3f, 1.3f)
        projection.translateRelative(0f, -0.3f)

        m.draw(projection)
    }

    private fun initFramework() {
        if (frameworkInitialized) return

        val option = CubismFramework.Option().apply {
            loadFileFunction = ICubismLoadFileFunction { path ->
                context.assets.open(path).use { it.readBytes() }
            }
        }

        CubismFramework.startUp(option)
        CubismFramework.initialize()
        frameworkInitialized = true
        Log.d(TAG, "CubismFramework initialized")
    }

    private fun loadModel() {
        try {
            val m = Live2dModel(context)
            m.loadAssets(MODEL_DIR, MODEL_JSON)
            m.setupRenderer(viewWidth, viewHeight)
            model = m
            lastTime = System.nanoTime()
            Log.d(TAG, "Model loaded: $MODEL_DIR$MODEL_JSON")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
        }
    }
}
