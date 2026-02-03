package com.retrivedmods.wclient.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.game.module.visual.ESPModule
import com.retrivedmods.wclient.game.module.visual.ChestESPModule
// Если добавил Xray, раскомментируй следующую строку:
// import com.retrivedmods.wclient.game.module.visual.XrayModule

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
                // Проверяем, включен ли модуль и есть ли игровая сессия
                if (module.isEnabled && module.isSessionCreated) {
                    
                    // 1. Отрисовка обычного ESP (игроки)
                    if (module is ESPModule) {
                        module.render(canvas)
                        hasActiveModule = true
                    }
                    
                    // 2. Отрисовка сундуков (ИСПРАВЛЕНО: вынес из предыдущего if)
                    if (module is ChestESPModule) {
                        module.render(canvas)
                        hasActiveModule = true
                    }

                    // 3. Если захочешь Xray, добавь сюда:
                    // if (module is XrayModule) {
                    //    module.render(canvas)
                    //    hasActiveModule = true
                    // }
                }
            }

            // Если хоть один модуль что-то рисует, обновляем экран постоянно
            if (hasActiveModule) {
                postInvalidateOnAnimation()
            }
        } catch (e: Exception) {
            // Если произошла ошибка отрисовки, пишем в лог, но не крашим игру
            e.printStackTrace()
            // Пробуем перезапустить отрисовку через 1 секунду
            postInvalidateDelayed(1000)
        }
    }
}
