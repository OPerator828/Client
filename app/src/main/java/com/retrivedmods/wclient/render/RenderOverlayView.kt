package com.retrivedmods.wclient.render

import android.content.Context
import android.graphics.Canvas
import android.view.View
import com.retrivedmods.wclient.game.ModuleManager
import com.retrivedmods.wclient.game.module.visual.ESPModule
import com.retrivedmods.wclient.game.module.visual.ChestESPModule

class RenderOverlayView(context: Context) : View(context) {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidate() 
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        ModuleManager.modules.forEach { module ->
            if (module.isEnabled && module.isSessionCreated) {
                // Отрисовка обычного ESP
                if (module is ESPModule) module.render(canvas)
                // Отрисовка сундуков
                if (module is ChestESPModule) module.render(canvas)
            }
        }

        // Заставляем экран обновляться, если включен любой ESP
        if (ModuleManager.modules.any { (it is ESPModule || it is ChestESPModule) && it.isEnabled && it.isSessionCreated }) {
            postInvalidateOnAnimation()
        }
    }
}
