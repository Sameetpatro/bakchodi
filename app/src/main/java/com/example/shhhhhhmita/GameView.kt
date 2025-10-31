package com.example.shhhhhhmita

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.example.shhhhhhmita.R
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // Public callback to notify score
    var onScoreChange: ((Int) -> Unit)? = null

    private var thread: Thread? = null
    @Volatile private var running = false
    private var paused = false

    private val bgPaint = Paint()
    private val snakePaint = Paint()
    private val foodPaint = Paint()

    private var canvasWidth = 0
    private var canvasHeight = 0

    // grid size (cell px)
    private var cellSize = 48
    private var cols = 0
    private var rows = 0

    private val snake = mutableListOf<Point>()
    private var direction = Direction.RIGHT
    private var nextDirection = Direction.RIGHT
    private var apple = Point(5, 5)
    private var score = 0
    private var speedMs = 150L // delay per move; lower => faster

    // Bitmaps
    private var headBitmap: Bitmap? = null
    private var bodyBitmap: Bitmap? = null
    private var foodBitmap: Bitmap? = null
    private var bgBitmap: Bitmap? = null

    // For swipe detection
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val SWIPE_THRESHOLD = 60

    init {
        holder.addCallback(this)
        isFocusable = true
        // load default images from drawable (user can replace files)
        loadResources()
    }

    private fun loadResources() {
        try {
            // Replace these resource names with your files in res/drawable
            headBitmap = BitmapFactory.decodeResource(resources, R.drawable.snake_face)
        } catch (e: Exception) {
            headBitmap = null
        }

        try {
            foodBitmap = BitmapFactory.decodeResource(resources, R.drawable.food)
        } catch (e: Exception) {
            foodBitmap = null
        }
        bodyBitmap = null
        bgBitmap = null
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        // compute how many cells fit horizontally and vertically
        cellSize = 50 // or whatever your cell size is
        cols = width / cellSize
        rows = height / cellSize
        Log.d("GameView", "width=$width height=$height cellSize=$cellSize")
        Log.d("GameView", "cols=$cols rows=$rows")

        initGame() // <-- only start the game now
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height
        // compute grid based on cellSize
        cols = canvasWidth / cellSize
        rows = (canvasHeight - 200) / cellSize // leave space maybe for UI
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running = false
        try {
            thread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun initGame() {
        // adjust cell size to screen width if necessary
        cellSize = (resources.displayMetrics.widthPixels / 20).coerceAtLeast(24)
        cols = canvasWidth / cellSize
        rows = (canvasHeight / cellSize).coerceAtLeast(10)
        snake.clear()
        val startX = (cols / 2)
        val startY = (rows / 2)
        snake.add(Point(startX, startY))
        snake.add(Point(startX - 1, startY))
        snake.add(Point(startX - 2, startY))
        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        spawnApple()
        score = 0
        onScoreChange?.invoke(score)
    }

    private fun spawnApple() {
        if (cols <= 0 || rows <= 0) return  // prevent crash if called too early

        val rnd = Random(System.currentTimeMillis())
        var p: Point
        do {
            p = Point(rnd.nextInt(cols), rnd.nextInt(rows))
        } while (snake.any { it.x == p.x && it.y == p.y })
        apple = p
    }


    override fun run() {
        var lastMove = System.currentTimeMillis()
        while (running) {
            if (!holder.surface.isValid) continue
            val now = System.currentTimeMillis()
            if (!paused && now - lastMove >= speedMs) {
                update()
                lastMove = now
            }
            draw()
            // small sleep to avoid busy loop and reduce CPU
            try {
                Thread.sleep(16)
            } catch (e: InterruptedException) {}
        }
    }

    private fun update() {
        // apply direction change (no 180-degree turn)
        if (!isOpposite(nextDirection, direction)) {
            direction = nextDirection
        }
        // compute new head pos
        val head = snake[0]
        val newHead = when (direction) {
            Direction.UP -> Point(head.x, head.y - 1)
            Direction.DOWN -> Point(head.x, head.y + 1)
            Direction.LEFT -> Point(head.x - 1, head.y)
            Direction.RIGHT -> Point(head.x + 1, head.y)
        }

        // wrap-around OR detect wall collision — choose wrap around:
        if (newHead.x < 0) newHead.x = cols - 1
        if (newHead.x >= cols) newHead.x = 0
        if (newHead.y < 0) newHead.y = rows - 1
        if (newHead.y >= rows) newHead.y = 0

        // collision with self -> game over (restart)
        if (snake.any { it.x == newHead.x && it.y == newHead.y }) {
            // Game Over: reset
            initGame()
            return
        }

        // move snake
        snake.add(0, newHead)
        if (newHead.x == apple.x && newHead.y == apple.y) {
            // ate apple
            score += 10
            onScoreChange?.invoke(score)
            spawnApple()
            // speed up slightly
            if (speedMs > 50) speedMs -= 3
        } else {
            // remove tail
            snake.removeAt(snake.size - 1)
        }
    }

    private fun isOpposite(a: Direction, b: Direction): Boolean {
        return (a == Direction.UP && b == Direction.DOWN)
                || (a == Direction.DOWN && b == Direction.UP)
                || (a == Direction.LEFT && b == Direction.RIGHT)
                || (a == Direction.RIGHT && b == Direction.LEFT)
    }

    private fun draw() {
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas == null) return
            canvas.drawColor(Color.BLACK)
            // draw background if present
            bgBitmap?.let {
                val src = Rect(0, 0, it.width, it.height)
                val dst = Rect(0, 0, width, height)
                canvas.drawBitmap(it, src, dst, null)
            }

            // translate grid origin a bit if top UI exists
            val offsetY = 0

            // draw apple (food)
            val appleLeft = apple.x * cellSize
            val appleTop = apple.y * cellSize + offsetY
            if (foodBitmap != null) {
                val bmp = Bitmap.createScaledBitmap(foodBitmap!!, cellSize, cellSize, false)
                canvas.drawBitmap(bmp, appleLeft.toFloat(), appleTop.toFloat(), foodPaint)
            } else {
                // fallback: draw circle
                val paint = Paint()
                paint.color = Color.RED
                canvas.drawCircle(
                    appleLeft + cellSize / 2f,
                    appleTop + cellSize / 2f,
                    cellSize / 2f,
                    paint
                )
            }

            // draw snake
            for (i in snake.indices) {
                val p = snake[i]
                val left = p.x * cellSize
                val top = p.y * cellSize + offsetY
                if (i == 0) {
                    // head
                    headBitmap?.let {
                        val body = Bitmap.createScaledBitmap(it, cellSize, cellSize, false)
                        val rotated = rotateBitmapForDirection(body, direction)
                        canvas.drawBitmap(rotated, left.toFloat(), top.toFloat(), snakePaint)
                    } ?: run {
                        // fallback head rect
                        val paint = Paint()
                        paint.color = Color.BLUE
                        canvas.drawRect(left.toFloat(), top.toFloat(), (left + cellSize).toFloat(), (top + cellSize).toFloat(), paint)
                    }
                } else {
                    bodyBitmap?.let {
                        val body = Bitmap.createScaledBitmap(it, cellSize, cellSize, false)
                        canvas.drawBitmap(body, left.toFloat(), top.toFloat(), snakePaint)
                    } ?: run {
                        val paint = Paint()
                        paint.color = Color.GREEN
                        canvas.drawRect(left.toFloat(), top.toFloat(), (left + cellSize).toFloat(), (top + cellSize).toFloat(), paint)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameView", "draw error", e)
        } finally {
            if (canvas != null) {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private fun rotateBitmapForDirection(bmp: Bitmap, dir: Direction): Bitmap {
        val matrix = Matrix()
        when (dir) {
            Direction.UP -> matrix.postRotate(270f)
            Direction.DOWN -> matrix.postRotate(90f)
            Direction.LEFT -> matrix.postRotate(180f)
            Direction.RIGHT -> matrix.postRotate(0f)
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }

    // Expose direction change from activity
    fun setDirection(dir: Direction) {
        // prevent 180-degree turns
        if (!isOpposite(dir, direction)) nextDirection = dir
    }

    // Pause/resume API
    fun pauseGame() {
        paused = true
    }

    fun resumeGame() {
        paused = false
    }

    fun isPaused() = paused

    // Touch handling - simple swipe detection
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchStartX
                val dy = event.y - touchStartY
                if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    if (kotlin.math.abs(dx) > SWIPE_THRESHOLD) {
                        if (dx > 0) setDirection(Direction.RIGHT) else setDirection(Direction.LEFT)
                    }
                } else {
                    if (kotlin.math.abs(dy) > SWIPE_THRESHOLD) {
                        if (dy > 0) setDirection(Direction.DOWN) else setDirection(Direction.UP)
                    }
                }
            }
        }
        return true
    }
}
