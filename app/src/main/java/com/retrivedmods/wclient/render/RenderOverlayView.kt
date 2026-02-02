package com.retrivedmods.wclient.render

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.game.module.visual.ESPModule
// 1. ДОБАВЬ ЭТОТ ИМПОРТ
import com.retrivedmods.wclient.game.module.visual.ChestESPModule

class RenderOverlayView(context: Context) : View(context) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate() 
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Отрисовка обычного ESP
        ModuleManager.modules
            .filterIsInstance<ESPModule>()
            .filter { it.isEnabled && it.isSessionCreated } 
            .forEach { it.render(canvas) }

        // 2. ДОБАВЬ ЭТОТ БЛОК ДЛЯ СУНДУКОВ
        ModuleManager.modules
            .filterIsInstance<ChestESPModule>()
            .filter { it.isEnabled && it.isSessionCreated }
            .forEach { it.render(canvas) }

        // 3. ОБНОВИ УСЛОВИЕ, ЧТОБЫ ЭКРАН ОБНОВЛЯЛСЯ И ДЛЯ СУНДУКОВ
        if (ModuleManager.modules.any {
                (it is ESPModule || it is ChestESPModule) && it.isEnabled && it.isSessionCreated
            }) {
            postInvalidateOnAnimation()
        }
    }
}
