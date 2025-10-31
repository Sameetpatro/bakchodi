package com.example.shhhhhhmita

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback, Runnable {

    // Public callback to notify score
    var onScoreChange: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null  // Callback for game over with final score

    private var thread: Thread? = null
    @Volatile private var running = false
    private var paused = false

    private val bgPaint = Paint()
    private val snakePaint = Paint()
    private val foodPaint = Paint()

    private var canvasWidth = 0
    private var canvasHeight = 0

    // grid size (cell px)
    private var cellSize = 100  // Increased to 100 for even bigger snake and food
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

    // For swipe detection
    private var touchStartX = 0f
    private var touchStartY = 0f
    private val SWIPE_THRESHOLD = 60

    private var initialized = false

    init {
        holder.addCallback(this)
        isFocusable = true
        // load default images from drawable
        loadResources()
    }

    private fun loadResources() {
        try {
            headBitmap = BitmapFactory.decodeResource(resources, R.drawable.snake_face)
            Log.d("GameView", "Head bitmap loaded")
        } catch (e: Exception) {
            Log.e("GameView", "Failed to load head bitmap", e)
            headBitmap = null
        }

        try {
            bodyBitmap = BitmapFactory.decodeResource(resources, R.drawable.snake_body)
            Log.d("GameView", "Body bitmap loaded")
        } catch (e: Exception) {
            Log.e("GameView", "Failed to load body bitmap", e)
            bodyBitmap = null
        }

        try {
            foodBitmap = BitmapFactory.decodeResource(resources, R.drawable.food)
            Log.d("GameView", "Food bitmap loaded")
        } catch (e: Exception) {
            Log.e("GameView", "Failed to load food bitmap", e)
            foodBitmap = null
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("GameView", "surfaceCreated called")
        canvasWidth = width
        canvasHeight = height

        if (canvasWidth > 0 && canvasHeight > 0 && !initialized) {
            // Calculate grid with very large cell size
            cellSize = (canvasWidth / 8).coerceAtLeast(90)  // Very big cells
            cols = canvasWidth / cellSize
            rows = (canvasHeight - 200) / cellSize

            Log.d("GameView", "Canvas: ${canvasWidth}x${canvasHeight}, Cell: $cellSize, Grid: ${cols}x${rows}")

            initGame()
            initialized = true
        }

        // Start the game thread
        running = true
        thread = Thread(this)
        thread?.start()
        Log.d("GameView", "Game thread started")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height

        if (!initialized && canvasWidth > 0 && canvasHeight > 0) {
            cellSize = (canvasWidth / 8).coerceAtLeast(90)  // Very big cells
            cols = canvasWidth / cellSize
            rows = (canvasHeight - 200) / cellSize

            Log.d("GameView", "surfaceChanged - Canvas: ${canvasWidth}x${canvasHeight}, Grid: ${cols}x${rows}")

            initGame()
            initialized = true
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("GameView", "surfaceDestroyed called")
        running = false
        try {
            thread?.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun initGame() {
        if (cols <= 0 || rows <= 0) {
            Log.w("GameView", "Cannot init game, invalid grid size: ${cols}x${rows}")
            return
        }

        snake.clear()
        val startX = (cols / 2).coerceAtLeast(3)
        val startY = (rows / 2).coerceAtLeast(1)

        snake.add(Point(startX, startY))
        snake.add(Point(startX - 1, startY))
        snake.add(Point(startX - 2, startY))

        direction = Direction.RIGHT
        nextDirection = Direction.RIGHT
        spawnApple()
        score = 0
        onScoreChange?.invoke(score)
        speedMs = 150L

        Log.d("GameView", "Game initialized - Snake at ($startX, $startY), Apple at (${apple.x}, ${apple.y})")
    }

    private fun spawnApple() {
        if (cols <= 0 || rows <= 0) return

        val rnd = Random(System.currentTimeMillis())
        var attempts = 0
        var p: Point

        do {
            p = Point(rnd.nextInt(cols), rnd.nextInt(rows))
            attempts++
        } while (snake.any { it.x == p.x && it.y == p.y } && attempts < 100)

        apple = p
        Log.d("GameView", "Apple spawned at (${apple.x}, ${apple.y})")
    }

    override fun run() {
        var lastMove = System.currentTimeMillis()
        Log.d("GameView", "Game loop started")

        while (running) {
            if (!holder.surface.isValid) {
                try {
                    Thread.sleep(16)
                } catch (e: InterruptedException) {}
                continue
            }

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
        Log.d("GameView", "Game loop ended")
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

        // wrap-around
        if (newHead.x < 0) newHead.x = cols - 1
        if (newHead.x >= cols) newHead.x = 0
        if (newHead.y < 0) newHead.y = rows - 1
        if (newHead.y >= rows) newHead.y = 0

        // collision with self -> game over (restart)
        if (snake.any { it.x == newHead.x && it.y == newHead.y }) {
            Log.d("GameView", "Game Over - Self collision!")
            pauseGame()
            onGameOver?.invoke(score)  // Notify activity of game over
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
            Log.d("GameView", "Apple eaten! Score: $score")
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

            // Draw gradient background
            val gradient = LinearGradient(
                0f, 0f, 0f, canvas.height.toFloat(),
                intArrayOf(0xFF1a237e.toInt(), 0xFF283593.toInt(), 0xFF3949ab.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), bgPaint)

            val offsetY = 100 // offset for top UI

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
                        val scaled = Bitmap.createScaledBitmap(it, cellSize, cellSize, false)
                        val rotated = rotateBitmapForDirection(scaled, direction)
                        canvas.drawBitmap(rotated, left.toFloat(), top.toFloat(), snakePaint)
                    } ?: run {
                        // fallback head rect
                        val paint = Paint()
                        paint.color = 0xFF4CAF50.toInt()
                        canvas.drawRect(
                            left.toFloat(),
                            top.toFloat(),
                            (left + cellSize).toFloat(),
                            (top + cellSize).toFloat(),
                            paint
                        )
                    }
                } else {
                    // body
                    bodyBitmap?.let {
                        val body = Bitmap.createScaledBitmap(it, cellSize, cellSize, false)
                        canvas.drawBitmap(body, left.toFloat(), top.toFloat(), snakePaint)
                    } ?: run {
                        val paint = Paint()
                        paint.color = 0xFF66BB6A.toInt()
                        canvas.drawRect(
                            left.toFloat(),
                            top.toFloat(),
                            (left + cellSize).toFloat(),
                            (top + cellSize).toFloat(),
                            paint
                        )
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
        if (!isOpposite(dir, direction)) {
            nextDirection = dir
            Log.d("GameView", "Direction changed to: $dir")
        }
    }

    // Pause/resume API
    fun pauseGame() {
        paused = true
        Log.d("GameView", "Game paused")
    }

    fun resumeGame() {
        paused = false
        Log.d("GameView", "Game resumed")
    }

    fun isPaused() = paused

    // Reset game for play again
    fun resetGame() {
        initGame()
    }

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