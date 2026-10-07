package com.example.converttable;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.world.item.Item;

/** Sends physical client input through MouseHandler, the screen, and the live server connection. */
final class UiGameTestInput {
    private UiGameTestInput() { }

    static void clickPanel(ClientGameTestContext context, int width, int height, int x, int y) {
        click(context, panelPosition(context,width,height,x,y));
    }

    static void hoverPanel(ClientGameTestContext context,int width,int height,int x,int y) {
        double[] position=panelPosition(context,width,height,x,y);
        context.getInput().setCursorPos(position[0],position[1]);
        context.waitTicks(3);
    }

    private static double[] panelPosition(ClientGameTestContext context,int width,int height,int x,int y) {
        double[] position = context.computeOnClient(mc -> {
            var screen = mc.gui.screen();
            var window = mc.getWindow();
            return new double[]{((screen.width - width) / 2 + x) * window.getScreenWidth() / (double) screen.width,
                ((screen.height - height) / 2 + y) * window.getScreenHeight() / (double) screen.height};
        });
        return position;
    }

    static void clickTarget(ClientGameTestContext context, Item output) {
        String name = context.computeOnClient(mc -> output.getDefaultInstance().getHoverName().getString());
        clickWidget(context, widget -> widget.getMessage().getString().contains(name));
    }

    static void clickWidget(ClientGameTestContext context, Predicate<AbstractWidget> matches) {
        // A slot/data packet can arrive before containerTick refreshes its dependent widgets.
        // Wait for the actual clickable control, then send physical input rather than a menu call.
        context.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().children().stream()
            .anyMatch(child -> child instanceof AbstractWidget widget && widget.visible && widget.active
                && matches.test(widget)), 100);
        double[] position = context.computeOnClient(mc -> {
            var screen = mc.gui.screen();
            for (var child : screen.children()) {
                if (child instanceof AbstractWidget widget && widget.visible && widget.active && matches.test(widget)) {
                    var window = mc.getWindow();
                    return new double[]{(widget.getX() + widget.getWidth() / 2.0) * window.getScreenWidth() / screen.width,
                        (widget.getY() + widget.getHeight() / 2.0) * window.getScreenHeight() / screen.height};
                }
            }
            String controls = screen.children().stream().filter(child -> child instanceof AbstractWidget)
                .map(child -> {
                    var widget = (AbstractWidget) child;
                    return widget.getMessage().getString() + " [visible=" + widget.visible + ", active=" + widget.active + "]";
                }).toList().toString();
            throw new AssertionError("No visible enabled widget matched on " + screen.getClass().getSimpleName() + ": " + controls);
        });
        click(context, position);
    }

    private static void click(ClientGameTestContext context, double[] position) {
        context.getInput().setCursorPos(position[0], position[1]);
        context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
        context.waitTicks(2);
    }
}
