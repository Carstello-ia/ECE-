package engine;

/**
 * Grants an extra life every {@link #POINTS_PER_LIFE} points
 * (Requirement 8 - Gameplay HUD, KAN-25) and tells the HUD how close the
 * player is to the next one.
 *
 * It holds no drawing code. The game screen feeds it the score once per
 * frame and adds the life itself, so the existing gained-life blink of
 * {@link HudAnimator} plays on its own.
 */
public class ExtraLifeTracker {

	/** Points needed for each extra life. */
	public static final int POINTS_PER_LIFE = 1000;
	/** How long, in ms, the bar flashes after an extra life. */
	public static final long FULL_FLASH_MS = 800;

	/** Score at which the next extra life is earned. */
	private int nextLifeAt;
	/** Time the last extra life was earned, or -1. */
	private long earnedAt = -1;

	/**
	 * @param score
	 *            Score of the run when the level starts. Lives already
	 *            earned below this score are not given again.
	 */
	public ExtraLifeTracker(final int score) {
		reset(score);
	}

	/**
	 * Recomputes the next threshold, e.g. after a restart.
	 *
	 * @param score
	 *            Current score.
	 */
	public final void reset(final int score) {
		this.nextLifeAt = (Math.max(0, score) / POINTS_PER_LIFE + 1)
				* POINTS_PER_LIFE;
		this.earnedAt = -1;
	}

	/**
	 * Checks the score against the next threshold. Called once per frame.
	 *
	 * @param now
	 *            Current time in ms.
	 * @param score
	 *            Current score.
	 * @param lives
	 *            Current lives.
	 * @param maxLives
	 *            Lives cap of the run.
	 * @return Number of lives to add this frame (0 when none, or when the
	 *         player is already at the cap: the threshold still moves on,
	 *         so the life is not stored for later).
	 */
	public int update(final long now, final int score, final int lives,
			final int maxLives) {
		int earned = 0;
		while (score >= this.nextLifeAt) {
			this.nextLifeAt += POINTS_PER_LIFE;
			if (lives + earned < maxLives)
				earned++;
			this.earnedAt = now;
		}
		return earned;
	}

	/**
	 * @param score
	 *            Current score.
	 * @return Progress toward the next extra life, from 0 to 1.
	 */
	public float getProgress(final int score) {
		int start = this.nextLifeAt - POINTS_PER_LIFE;
		float progress = (float) (score - start) / POINTS_PER_LIFE;
		return Math.max(0f, Math.min(1f, progress));
	}

	/**
	 * @return Score at which the next extra life is earned.
	 */
	public int getNextLifeAt() {
		return this.nextLifeAt;
	}

	/**
	 * @param now
	 *            Current time in ms.
	 * @return True while the bar flashes after an extra life.
	 */
	public boolean isFullFlashOn(final long now) {
		return this.earnedAt >= 0 && now - this.earnedAt < FULL_FLASH_MS
				&& ((now - this.earnedAt) / 100) % 2 == 0;
	}
}
