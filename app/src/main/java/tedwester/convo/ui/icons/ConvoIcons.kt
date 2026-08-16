package tedwester.convo.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import tedwester.convo.R

/**
 * App icons from [Lucide](https://lucide.dev) (thin stroke vectors in `res/drawable`).
 */
object ConvoIcons {
    /** Lucide `cog` — settings. */
    @Composable
    fun Cog(): Painter = painterResource(R.drawable.ic_lucide_cog)

    /** Lucide `monitor-cog` — system message / AI instructions. */
    @Composable
    fun MonitorCog(): Painter = painterResource(R.drawable.ic_lucide_monitor_cog)

    /** Lucide `search`. */
    @Composable
    fun Search(): Painter = painterResource(R.drawable.ic_lucide_search)

    /** Lucide `brain` — reasoning toggle. */
    @Composable
    fun Brain(): Painter = painterResource(R.drawable.ic_lucide_brain)

    /** Lucide `mic` — dictate into the composer. */
    @Composable
    fun Mic(): Painter = painterResource(R.drawable.ic_lucide_mic)

    /** Lucide `audio-lines` — voice / waveform. */
    @Composable
    fun AudioLines(): Painter = painterResource(R.drawable.ic_lucide_audio_lines)

    /** Lucide `play` — audio playback. */
    @Composable
    fun Play(): Painter = painterResource(R.drawable.ic_lucide_play)

    /** Lucide `pause` — audio playback. */
    @Composable
    fun Pause(): Painter = painterResource(R.drawable.ic_lucide_pause)

    /** Lucide `download` — save audio reply to Downloads. */
    @Composable
    fun Download(): Painter = painterResource(R.drawable.ic_lucide_download)

    /** Lucide `type` — show transcribed / spoken text for a voice note. */
    @Composable
    fun Type(): Painter = painterResource(R.drawable.ic_lucide_type)

    /** Lucide `type-outline` — show voice note audio player. */
    @Composable
    fun TypeOutline(): Painter = painterResource(R.drawable.ic_lucide_type_outline)

    /** Lucide `arrow-left` — navigate back. */
    @Composable
    fun ArrowLeft(): Painter = painterResource(R.drawable.ic_lucide_arrow_left)

    /** Lucide `arrow-right-to-line` — back to chat. */
    @Composable
    fun ArrowRightToLine(): Painter = painterResource(R.drawable.ic_lucide_arrow_right_to_line)

    /** Lucide `square-pen` — new chat. */
    @Composable
    fun SquarePen(): Painter = painterResource(R.drawable.ic_lucide_square_pen)

    /** Lucide `circle-user` — account / chat history menu. */
    @Composable
    fun CircleUser(): Painter = painterResource(R.drawable.ic_lucide_circle_user)

    /** Lucide `key`. */
    @Composable
    fun Key(): Painter = painterResource(R.drawable.ic_lucide_key)

    /** Lucide `credit-card` — credits balance. */
    @Composable
    fun CreditCard(): Painter = painterResource(R.drawable.ic_lucide_credit_card)

    /** Lucide `eye`. */
    @Composable
    fun Eye(): Painter = painterResource(R.drawable.ic_lucide_eye)

    /** Lucide `eye-off`. */
    @Composable
    fun EyeOff(): Painter = painterResource(R.drawable.ic_lucide_eye_off)

    /** Lucide `folder` — projects. */
    @Composable
    fun Folder(): Painter = painterResource(R.drawable.ic_lucide_folder)

    /** Lucide `folder-plus` — add to project. */
    @Composable
    fun FolderPlus(): Painter = painterResource(R.drawable.ic_lucide_folder_plus)

    /** Lucide `pin`. */
    @Composable
    fun Pin(): Painter = painterResource(R.drawable.ic_lucide_pin)

    /** Lucide `archive`. */
    @Composable
    fun Archive(): Painter = painterResource(R.drawable.ic_lucide_archive)

    /** Lucide `trash-2`. */
    @Composable
    fun Trash2(): Painter = painterResource(R.drawable.ic_lucide_trash_2)

    /** Lucide `x` — clear / remove. */
    @Composable
    fun X(): Painter = painterResource(R.drawable.ic_lucide_x)

    /** Lucide `ellipsis-vertical` — overflow menu. */
    @Composable
    fun EllipsisVertical(): Painter = painterResource(R.drawable.ic_lucide_ellipsis_vertical)

    /** Lucide `menu` — drag handle / reorder. */
    @Composable
    fun Menu(): Painter = painterResource(R.drawable.ic_lucide_menu)

    /** Lucide `lock` — lock the app. */
    @Composable
    fun Lock(): Painter = painterResource(R.drawable.ic_lucide_lock)

    /** Lucide `circle-help` — help / guide. */
    @Composable
    fun CircleHelp(): Painter = painterResource(R.drawable.ic_lucide_circle_help)

    /** Lucide `chevron-down`. */
    @Composable
    fun ChevronDown(): Painter = painterResource(R.drawable.ic_lucide_chevron_down)

    /** Lucide `chevron-up`. */
    @Composable
    fun ChevronUp(): Painter = painterResource(R.drawable.ic_lucide_chevron_up)


    /** Lucide `chevron-left`. */
    @Composable
    fun ChevronLeft(): Painter = painterResource(R.drawable.ic_lucide_chevron_left)

    /** Lucide `chevron-right`. */
    @Composable
    fun ChevronRight(): Painter = painterResource(R.drawable.ic_lucide_chevron_right)

    /** Lucide `loader-circle` — spinning progress. */
    @Composable
    fun LoaderCircle(): Painter = painterResource(R.drawable.ic_lucide_loader_circle)

    /** Lucide `check` — completed unread reply. */
    @Composable
    fun Check(): Painter = painterResource(R.drawable.ic_lucide_check)

    /** Lucide `repeat` — regenerate response. */
    @Composable
    fun Repeat(): Painter = painterResource(R.drawable.ic_lucide_repeat)

    /** Lucide `copy`. */
    @Composable
    fun Copy(): Painter = painterResource(R.drawable.ic_lucide_copy)

    /** Lucide `circle-check` — copy success feedback. */
    @Composable
    fun CircleCheck(): Painter = painterResource(R.drawable.ic_lucide_circle_check)

    /** Lucide `clipboard-paste`. */
    @Composable
    fun ClipboardPaste(): Painter = painterResource(R.drawable.ic_lucide_clipboard_paste)

    /** Lucide `share-2`. */
    @Composable
    fun Share2(): Painter = painterResource(R.drawable.ic_lucide_share_2)

    /** Lucide `images` — photo gallery. */
    @Composable
    fun Images(): Painter = painterResource(R.drawable.ic_lucide_images)

    /** Lucide `camera`. */
    @Composable
    fun Camera(): Painter = painterResource(R.drawable.ic_lucide_camera)

    /** Lucide `file`. */
    @Composable
    fun File(): Painter = painterResource(R.drawable.ic_lucide_file)

    /** Thin plus used on the new-chat FAB. */
    @Composable
    fun Add(): Painter = painterResource(R.drawable.ic_convo_add)

    @Composable
    fun Close(): Painter = painterResource(R.drawable.ic_convo_close)

    /** @deprecated Use [Cog]. */
    @Composable
    fun Settings(): Painter = Cog()

    /** @deprecated Use [ArrowRightToLine]. */
    @Composable
    fun ArrowRight(): Painter = ArrowRightToLine()

    @Composable
    fun of(@DrawableRes id: Int): Painter = painterResource(id)
}
