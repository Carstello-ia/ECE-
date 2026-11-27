package screen;

import java.awt.Color;
import java.awt.event.KeyEvent;

import engine.SpaceOpera;
import engine.soundEngine.MenuSounds;
import engine.soundEngine.Sound;
import engine.soundEngine.SoundManager;

/**
 * Mission briefing before the first stage of the Space Opera mode (Team
 * ECE - KAN-57): the story typed out line by line over the stars, over
 * the orchestral fanfare (KAN-65).
 *
 * Space shows the whole text at once, then launches the run; esc goes back
 * to the menu.
 */
public class MissionBriefingScreen extends Screen {

	/** Gap between two lines of the briefing. */
	private static final int LINE_SPACING = 18;

	/** Stars drifting behind the text. */
	private final Starfield starfield;
	/** When the briefing opened. */
	private long openedAt;
	/** True once the player skipped the typing. */
	private boolean skipped;
	/** Whether space was down on the previous frame. */
	private boolean spaceWasDown = true;

	/**
	 * @param width
	 *            Screen width.
	 * @param height
	 *            Screen height.
	 * @param fps
	 *            Frames per second.
	 */
	public MissionBriefingScreen(final int width, final int height,
			final int fps) {
		super(width, height, fps);
		this.returnCode = 2;
		this.starfield = new Starfield(width, height);
	}

	/** @return 2 to launch the run, 1 to go back to the menu. */
	@Override
	public final int run() {
		this.openedAt = System.currentTimeMillis();
		// Orchestral fanfare for the opening (Team ECE - KAN-65).
		SoundManager.getInstance().playBgm(Sound.BGM_SPACE_OPERA_BRIEFING);
		super.run();
		return this.returnCode;
	}

	@Override
	protected final void update() {
		super.update();
		this.starfield.update();
		boolean spaceDown = this.inputManager.isKeyDown(KeyEvent.VK_SPACE)
				|| this.inputManager.isKeyDown(KeyEvent.VK_ENTER);
		if (this.inputDelay.checkFinished()) {
			if (this.inputManager.isKeyDown(KeyEvent.VK_ESCAPE)) {
				MenuSounds.playCancel();
				this.returnCode = 1;
				this.isRunning = false;
			} else if (spaceDown && !this.spaceWasDown) {
				if (isTyping()) {
					this.skipped = true;
				} else {
					MenuSounds.playSelect();
					this.returnCode = 2;
					this.isRunning = false;
				}
			}
		}
		this.spaceWasDown = spaceDown;
		draw();
	}

	/** @return True while the text is still being typed. */
	private boolean isTyping() {
		return !this.skipped && SpaceOpera.briefingCharsShown(
				System.currentTimeMillis() - this.openedAt) < SpaceOpera
						.briefingLength();
	}

	/** Draws the stars, the title and the typed lines. */
	private void draw() {
		this.drawManager.initDrawing(this);
		this.drawManager.drawSpaceBackground(this, this.starfield, 0f);
		this.drawManager.drawScreenTitle(this, "MISSION BRIEFING");

		int shown = this.skipped ? Integer.MAX_VALUE
				: SpaceOpera.briefingCharsShown(
						System.currentTimeMillis() - this.openedAt);
		int y = this.getHeight() / 4 + 10;
		for (String line : SpaceOpera.BRIEFING) {
			if (shown <= 0)
				break;
			String text = line.length() <= shown ? line
					: line.substring(0, shown);
			shown -= line.length();
			this.drawManager.drawCenteredSmallString(this, text, y,
					new Color(255, 215, 90));
			y += LINE_SPACING;
		}
		this.drawManager.drawKeyHints(this, isTyping()
				? "space skip, esc back" : "space launch, esc back");
		this.drawManager.completeDrawing(this);
	}
}
