package tedwester.convo.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import tedwester.convo.R

object ConvoIcons {

    @Composable
    fun Cog(): Painter = painterResource(R.drawable.ic_lucide_cog)

    @Composable
    fun MonitorCog(): Painter = painterResource(R.drawable.ic_lucide_monitor_cog)

    @Composable
    fun Search(): Painter = painterResource(R.drawable.ic_lucide_search)

    @Composable
    fun Brain(): Painter = painterResource(R.drawable.ic_lucide_brain)

    @Composable
    fun Mic(): Painter = painterResource(R.drawable.ic_lucide_mic)

    @Composable
    fun AudioLines(): Painter = painterResource(R.drawable.ic_lucide_audio_lines)

    @Composable
    fun Play(): Painter = painterResource(R.drawable.ic_lucide_play)

    @Composable
    fun Pause(): Painter = painterResource(R.drawable.ic_lucide_pause)

    @Composable
    fun Download(): Painter = painterResource(R.drawable.ic_lucide_download)

    @Composable
    fun Type(): Painter = painterResource(R.drawable.ic_lucide_type)

    @Composable
    fun TypeOutline(): Painter = painterResource(R.drawable.ic_lucide_type_outline)

    @Composable
    fun ArrowLeft(): Painter = painterResource(R.drawable.ic_lucide_arrow_left)

    @Composable
    fun ArrowRightToLine(): Painter = painterResource(R.drawable.ic_lucide_arrow_right_to_line)

    @Composable
    fun SquarePen(): Painter = painterResource(R.drawable.ic_lucide_square_pen)

    @Composable
    fun CircleUser(): Painter = painterResource(R.drawable.ic_lucide_circle_user)

    @Composable
    fun Key(): Painter = painterResource(R.drawable.ic_lucide_key)

    @Composable
    fun CreditCard(): Painter = painterResource(R.drawable.ic_lucide_credit_card)

    @Composable
    fun Eye(): Painter = painterResource(R.drawable.ic_lucide_eye)

    @Composable
    fun EyeOff(): Painter = painterResource(R.drawable.ic_lucide_eye_off)

    @Composable
    fun Folder(): Painter = painterResource(R.drawable.ic_lucide_folder)

    @Composable
    fun FolderPlus(): Painter = painterResource(R.drawable.ic_lucide_folder_plus)

    @Composable
    fun Pin(): Painter = painterResource(R.drawable.ic_lucide_pin)

    @Composable
    fun Archive(): Painter = painterResource(R.drawable.ic_lucide_archive)

    @Composable
    fun Trash2(): Painter = painterResource(R.drawable.ic_lucide_trash_2)

    @Composable
    fun X(): Painter = painterResource(R.drawable.ic_lucide_x)

    @Composable
    fun EllipsisVertical(): Painter = painterResource(R.drawable.ic_lucide_ellipsis_vertical)

    @Composable
    fun Menu(): Painter = painterResource(R.drawable.ic_lucide_menu)

    @Composable
    fun Lock(): Painter = painterResource(R.drawable.ic_lucide_lock)

    @Composable
    fun CircleHelp(): Painter = painterResource(R.drawable.ic_lucide_circle_help)

    @Composable
    fun ChevronDown(): Painter = painterResource(R.drawable.ic_lucide_chevron_down)

    @Composable
    fun ChevronUp(): Painter = painterResource(R.drawable.ic_lucide_chevron_up)

    @Composable
    fun ChevronLeft(): Painter = painterResource(R.drawable.ic_lucide_chevron_left)

    @Composable
    fun ChevronRight(): Painter = painterResource(R.drawable.ic_lucide_chevron_right)

    @Composable
    fun LoaderCircle(): Painter = painterResource(R.drawable.ic_lucide_loader_circle)

    @Composable
    fun Check(): Painter = painterResource(R.drawable.ic_lucide_check)

    @Composable
    fun Repeat(): Painter = painterResource(R.drawable.ic_lucide_repeat)

    @Composable
    fun Copy(): Painter = painterResource(R.drawable.ic_lucide_copy)

    @Composable
    fun CircleCheck(): Painter = painterResource(R.drawable.ic_lucide_circle_check)

    @Composable
    fun ClipboardPaste(): Painter = painterResource(R.drawable.ic_lucide_clipboard_paste)

    @Composable
    fun Share2(): Painter = painterResource(R.drawable.ic_lucide_share_2)

    @Composable
    fun Images(): Painter = painterResource(R.drawable.ic_lucide_images)

    @Composable
    fun Camera(): Painter = painterResource(R.drawable.ic_lucide_camera)

    @Composable
    fun File(): Painter = painterResource(R.drawable.ic_lucide_file)

    @Composable
    fun Add(): Painter = painterResource(R.drawable.ic_convo_add)

    @Composable
    fun Close(): Painter = painterResource(R.drawable.ic_convo_close)

    @Composable
    fun Settings(): Painter = Cog()

    @Composable
    fun ArrowRight(): Painter = ArrowRightToLine()

    @Composable
    fun of(@DrawableRes id: Int): Painter = painterResource(id)
}
