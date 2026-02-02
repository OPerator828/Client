package com.retrivedmods.wclient.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.game.module.visual.ESPModule
import com.retrivedmods.wclient.game.module.visual.ChestESPModule

class RenderOverlayView(context: Context) : View(context) {

    init {
        // Делаем view прозрачным, чтобы не блокировать игру
        setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate() 
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        try {
            var hasActiveModule = false

            ModuleManager.modules.forEach { module ->
                if (module.isEnabled && module.isSessionCreated) {
                    // Отрисовка обычного ESP
                    if (module is ESPModule) {
                        module.render(canvas)
                        hasActiveModule = true
                    }
                    // Отрисовка сундуков
                    if (module is ChestESPModule) {
                        module.render(canvas)
                        hasActiveModule = true
                    }
                }
            }

            // Заставляем экран обновляться, если включен любой ESP
            if (hasActiveModule) {
                postInvalidateOnAnimation()
            }
        } catch (e: Exception) {
            // Обработка ошибок, чтобы не крашить приложение
            e.printStackTrace()
            // Попробуем снова через секунду
            postInvalidateDelayed(1000)
        }
    }
}
