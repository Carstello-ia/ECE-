package engine;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.util.Locale;

/**
 * In-game coin and diamond balance HUD (Team ECE - Requirement 8,
 * Gameplay HUD, KAN-10).
 *
 * Improves the static balance numbers with feedback the player can read
 * without looking away from the action:
 *
 * - Rolling counter: the shown number counts up (or down) to the real
 *   balance instead of jumping, so a big pickup is noticeable.
 * - Gain / spend popup: "+N" (coin yellow / diamond cyan) or "-N" (red)
 *   rises next to the counter and fades out. Pickups close together are
 *   merged into one popup, so a coin shower reads "+12", not twelve "+1".
 * - Icon pulse: the icon briefly grows and brightens on a gain, and
 *   flashes red on a spend.
 * - Compact numbers: 10000 and above are shown as "10.0K" / "1.2M", so a
 *   large balance never runs off the screen edge.
 *
 * Pure display: the balances themselves are owned by the Currency team
 * (CurrencyManager / DiamondManager); this class only reads them.
 * Animations are time based, so they run at the same speed whatever the
 * frame rate.
 */
public class CurrencyHud {

	/** Baseline Y of both counters (row below the level indicator). */
	public static final int COUNTER_Y = 36;
	/** Size of the coin icon (px), same as the shop balance. */
	private static final int COIN_ICON_SIZE = 14;
	/** Size of the diamond icon (px), same as before. */
	private static final int DIAMOND_ICON_SIZE = 10;
	/** Gap between an icon and its number (px). */
	private static final int ICON_GAP = 5;
	/** Space kept from the right edge, aligned with the score (px). */
	private static final int RIGHT_MARGIN = 8;
	/** Gap between the counter and its popup (px). */
	private static final int POPUP_GAP = 6;

	/** Time for the rolling counter to cover most of a change (ms). */
	private static final double ROLL_TIME = 150.0;
	/** Duration of the icon pulse (ms). */
	private static final int PULSE_TIME = 250;
	/** Extra icon size at the start of a pulse (fraction of size). */
	private static final double PULSE_GROWTH = 0.3;
	/** Lifetime of a popup (ms). */
	private static final int POPUP_TIME = 900;
	/** Popup starts fading after this fraction of its life. */
	private static final double POPUP_FADE_START = 0.55;
	/** Distance the coin popup rises over its life (px). The diamond
	 *  popup stays on its row, since the score sits right above it. */
	private static final int POPUP_RISE = 8;
	/** Spend flash keeps the icon fully red for this part of the pulse. */
	private static final double SPEND_FLASH = 0.6;
	/** Changes closer together than this are merged in one popup (ms). */
	private static final int MERGE_WINDOW = 600;
	/** Balances from this value on are shown in compact form. */
	private static final int COMPACT_FROM = 10000;

	/** Coin color, same yellow as the dropped coins. */
	private static final Color COIN_COLOR = Color.YELLOW;
	/** Diamond color, same cyan as the dropped diamonds. */
	private static final Color DIAMOND_COLOR = Color.CYAN;
	/** Color used when the balance goes down. */
	private static final Color SPEND_COLOR = new Color(255, 80, 80);
	/** Outline drawn under the popup so it stays readable. */
	private static final Color OUTLINE = Color.BLACK;

	/** Coin counter. */
	private final Track coins = new Track();
	/** Diamond counter. */
	private final Track diamonds = new Track();

	/**
	 * Animation state of one balance counter.
	 */
	static final class Track {
		/** Real balance last seen. */
		int target;
		/** Value currently shown (moves toward target). */
		double shown;
		/** Whether the first value has been read yet. */
		boolean started;
		/** Time of the last update (ms). */
		long lastUpdate;
		/** Start time of the current pulse (ms), or -1. */
		long pulseStart = -1;
		/** Whether the current pulse/popup is a spend. */
		boolean spending;
		/** Amount shown in the popup (always positive). */
		int popupAmount;
		/** Start time of the popup (ms), or -1. */
		long popupStart = -1;
		/** Time of the last change merged into the popup (ms). */
		long lastChange = -1;

		/**
		 * Reads the real balance and advances the animation.
		 *
		 * @param value Real balance.
		 * @param now   Current time (ms).
		 */
		void update(final int value, final long now) {
			if (!started) {
				// First frame: show the balance as is, no popup. The
				// balance carries over between levels.
				started = true;
				target = value;
				shown = value;
				lastUpdate = now;
				return;
			}

			int delta = value - target;
			if (delta != 0) {
				boolean spend = delta < 0;
				boolean merge = popupStart >= 0 && spend == spending
						&& now - lastChange < MERGE_WINDOW;
				popupAmount = merge ? popupAmount + Math.abs(delta)
						: Math.abs(delta);
				spending = spend;
				popupStart = now;
				pulseStart = now;
				lastChange = now;
				target = value;
			}

			long dt = Math.max(0, now - lastUpdate);
			lastUpdate = now;
			double diff = target - shown;
			if (Math.abs(diff) < 1.0) {
				shown = target;
			} else {
				shown += diff * Math.min(1.0, dt / ROLL_TIME);
			}
		}

		/** @return Value to print this frame. */
		int shownValue() {
			return (int) Math.round(shown);
		}

		/**
		 * @param now Current time (ms).
		 * @return Pulse progress from 0 (start) to 1 (done).
		 */
		double pulse(final long now) {
			if (pulseStart < 0)
				return 1.0;
			return Math.min(1.0, (now - pulseStart) / (double) PULSE_TIME);
		}

		/**
		 * @param now Current time (ms).
		 * @return Popup progress from 0 (start) to 1 (gone).
		 */
		double popup(final long now) {
			if (popupStart < 0)
				return 1.0;
			return Math.min(1.0, (now - popupStart) / (double) POPUP_TIME);
		}
	}

	/**
	 * Reads the current balances. Call once per frame before drawing.
	 *
	 * @param coinBalance    Current coin balance.
	 * @param diamondBalance Current diamond balance.
	 * @param now            Current time (ms).
	 */
	public void update(final int coinBalance, final int diamondBalance,
			final long now) {
		this.coins.update(coinBalance, now);
		this.diamonds.update(diamondBalance, now);
	}

	/**
	 * Draws both counters and their popups.
	 *
	 * @param g     Graphics of the back buffer.
	 * @param width Screen width.
	 * @param font  Regular game font.
	 * @param now   Current time (ms).
	 */
	public void draw(final Graphics g, final int width, final Font font,
			final long now) {
		if (g == null || font == null)
			return;
		g.setFont(font);
		FontMetrics metrics = g.getFontMetrics(font);

		// Coins: centred under the level indicator, popup on the right.
		String coinText = format(this.coins.shownValue());
		int coinWidth = COIN_ICON_SIZE + ICON_GAP
				+ metrics.stringWidth(coinText);
		int coinX = (width - coinWidth) / 2;
		drawCounter(g, this.coins, coinText, coinX, true,
				COIN_COLOR, COIN_ICON_SIZE, now);
		drawPopup(g, metrics, this.coins, coinX + coinWidth + POPUP_GAP,
				false, POPUP_RISE, COIN_COLOR, now);

		// Diamonds: right-aligned under the score, popup on the left so
		// it never leaves the screen.
		String diamondText = format(this.diamonds.shownValue());
		int diamondWidth = DIAMOND_ICON_SIZE + ICON_GAP
				+ metrics.stringWidth(diamondText);
		int diamondX = width - RIGHT_MARGIN - diamondWidth;
		drawCounter(g, this.diamonds, diamondText, diamondX,
				false, DIAMOND_COLOR, DIAMOND_ICON_SIZE, now);
		drawPopup(g, metrics, this.diamonds, diamondX - POPUP_GAP, true,
				0, DIAMOND_COLOR, now);
	}

	/**
	 * Draws one icon and its number.
	 */
	private static void drawCounter(final Graphics g,
			final Track track, final String text,
			final int startX, final boolean coin, final Color color,
			final int iconSize, final long now) {
		double p = track.pulse(now);
		double ease = 1.0 - p;
		int size = (int) Math.round(iconSize * (1.0 + PULSE_GROWTH * ease));
		int centerX = startX + iconSize / 2;
		int centerY = COUNTER_Y - iconSize / 2;
		int left = centerX - size / 2;
		int top = centerY - size / 2;

		Color iconColor = color;
		Color textColor = Color.WHITE;
		if (p < 1.0) {
			if (track.spending) {
				// Plain flash: blending red into cyan/yellow looks muddy.
				boolean flash = p < SPEND_FLASH;
				iconColor = flash ? SPEND_COLOR : color;
				textColor = flash ? SPEND_COLOR : Color.WHITE;
			} else {
				iconColor = mix(color, Color.WHITE, ease * 0.6);
				textColor = mix(Color.WHITE, color, ease);
			}
		}

		g.setColor(iconColor);
		if (coin) {
			g.fillOval(left, top, size, size);
		} else {
			int[] xs = { centerX, left + size, centerX, left };
			int[] ys = { top, centerY, top + size, centerY };
			g.fillPolygon(xs, ys, 4);
		}
		g.setColor(textColor);
		g.drawString(text, startX + iconSize + ICON_GAP, COUNTER_Y);
	}

	/**
	 * Draws the "+N" / "-N" popup of a counter, if one is active.
	 *
	 * @param anchorX   X where the popup starts (left edge), or ends
	 *                  (right edge) when alignRight is true.
	 * @param rise      Distance the popup rises over its life (px).
	 */
	private static void drawPopup(final Graphics g, final FontMetrics metrics,
			final Track track, final int anchorX, final boolean alignRight,
			final int rise, final Color color, final long now) {
		double p = track.popup(now);
		if (p >= 1.0 || track.popupAmount <= 0)
			return;

		String text = (track.spending ? "-" : "+") + format(track.popupAmount);
		int x = alignRight ? anchorX - metrics.stringWidth(text) : anchorX;
		int y = COUNTER_Y - (int) Math.round(rise * p);

		float alpha = 1f;
		if (p > POPUP_FADE_START)
			alpha = (float) ((1.0 - p) / (1.0 - POPUP_FADE_START));
		Color base = track.spending ? SPEND_COLOR : color;

		g.setColor(withAlpha(OUTLINE, alpha));
		g.drawString(text, x + 1, y + 1);
		g.setColor(withAlpha(base, alpha));
		g.drawString(text, x, y);
	}

	/**
	 * Formats a balance, compact from 10000 on ("12.3K", "1.2M").
	 *
	 * @param value Balance.
	 * @return Text to print.
	 */
	static String format(final int value) {
		int abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs < COMPACT_FROM)
			return Integer.toString(value);
		if (abs < 1000000)
			return sign + String.format(Locale.ROOT, "%.1fK", abs / 1000.0);
		return sign + String.format(Locale.ROOT, "%.1fM", abs / 1000000.0);
	}

	/** @return Color between a and b (t = 0 gives a, t = 1 gives b). */
	private static Color mix(final Color a, final Color b, final double t) {
		double k = Math.max(0.0, Math.min(1.0, t));
		return new Color(
				(int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
				(int) Math.round(a.getGreen()
						+ (b.getGreen() - a.getGreen()) * k),
				(int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
	}

	/** @return The color with the given opacity (0 to 1). */
	private static Color withAlpha(final Color c, final float alpha) {
		int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	/** @return Coin counter state (for tests). */
	Track coinTrack() {
		return this.coins;
	}

	/** @return Diamond counter state (for tests). */
	Track diamondTrack() {
		return this.diamonds;
	}
}
