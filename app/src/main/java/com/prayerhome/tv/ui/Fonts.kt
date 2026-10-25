package com.prayerhome.tv.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.prayerhome.tv.R

/** The typefaces used by the ten designs, trimmed to the weights each one actually uses. */
object Fonts {
    private fun w(n: Int) = FontWeight(n)

    val Alexandria = FontFamily(Font(R.font.alexandria_500, w(500)), Font(R.font.alexandria_600, w(600)))
    val Amiri = FontFamily(Font(R.font.amiri_400, w(400)), Font(R.font.amiri_700, w(700)))
    val Archivo = FontFamily(Font(R.font.archivo_400, w(400)), Font(R.font.archivo_500, w(500)), Font(R.font.archivo_600, w(600)))
    val ArefRuqaa = FontFamily(Font(R.font.arefruqaa_700, w(700)))
    val Barlow = FontFamily(Font(R.font.barlow_300, w(300)), Font(R.font.barlow_400, w(400)), Font(R.font.barlow_500, w(500)), Font(R.font.barlow_600, w(600)))
    val BarlowCondensed = FontFamily(
        Font(R.font.barlowcondensed_400, w(400)), Font(R.font.barlowcondensed_500, w(500)),
        Font(R.font.barlowcondensed_600, w(600)), Font(R.font.barlowcondensed_700, w(700)),
    )
    val Blaka = FontFamily(Font(R.font.blaka_400, w(400)))
    val Cinzel = FontFamily(Font(R.font.cinzel_400, w(400)), Font(R.font.cinzel_500, w(500)), Font(R.font.cinzel_600, w(600)))
    val Cormorant = FontFamily(Font(R.font.cormorantgaramond_400, w(400)), Font(R.font.cormorantgaramond_500, w(500)), Font(R.font.cormorantgaramond_600, w(600)))
    val DmSerif = FontFamily(Font(R.font.dmserifdisplay_400, w(400)), Font(R.font.dmserifdisplay_400_italic, w(400), FontStyle.Italic))
    val ElMessiri = FontFamily(Font(R.font.elmessiri_500, w(500)), Font(R.font.elmessiri_600, w(600)), Font(R.font.elmessiri_700, w(700)))
    val Figtree = FontFamily(Font(R.font.figtree_300, w(300)), Font(R.font.figtree_400, w(400)), Font(R.font.figtree_500, w(500)), Font(R.font.figtree_600, w(600)))
    val Fraunces = FontFamily(Font(R.font.fraunces_300, w(300)), Font(R.font.fraunces_400, w(400)), Font(R.font.fraunces_500, w(500)))
    val Italiana = FontFamily(Font(R.font.italiana_400, w(400)))
    val JetBrains = FontFamily(Font(R.font.jetbrainsmono_300, w(300)), Font(R.font.jetbrainsmono_400, w(400)), Font(R.font.jetbrainsmono_500, w(500)))
    val Josefin = FontFamily(Font(R.font.josefinsans_200, w(200)), Font(R.font.josefinsans_300, w(300)), Font(R.font.josefinsans_400, w(400)), Font(R.font.josefinsans_600, w(600)))
    val Jost = FontFamily(Font(R.font.jost_300, w(300)), Font(R.font.jost_400, w(400)), Font(R.font.jost_500, w(500)), Font(R.font.jost_600, w(600)))
    val Karla = FontFamily(Font(R.font.karla_300, w(300)), Font(R.font.karla_400, w(400)), Font(R.font.karla_600, w(600)))
    val Kufam = FontFamily(Font(R.font.kufam_500, w(500)), Font(R.font.kufam_600, w(600)))
    val Manrope = FontFamily(Font(R.font.manrope_300, w(300)), Font(R.font.manrope_400, w(400)), Font(R.font.manrope_500, w(500)), Font(R.font.manrope_600, w(600)))
    val Marcellus = FontFamily(Font(R.font.marcellus_400, w(400)))
    val Mulish = FontFamily(Font(R.font.mulish_300, w(300)), Font(R.font.mulish_400, w(400)), Font(R.font.mulish_500, w(500)), Font(R.font.mulish_600, w(600)))
    val NotoKufi = FontFamily(Font(R.font.notokufiarabic_500, w(500)), Font(R.font.notokufiarabic_600, w(600)))
    val NotoNaskh = FontFamily(Font(R.font.notonaskharabic_600, w(600)), Font(R.font.notonaskharabic_700, w(700)))
    val Qahiri = FontFamily(Font(R.font.qahiri_400, w(400)))
    val Rakkas = FontFamily(Font(R.font.rakkas_400, w(400)))
    val ReemKufi = FontFamily(Font(R.font.reemkufi_500, w(500)), Font(R.font.reemkufi_600, w(600)))
    val Ruwudu = FontFamily(Font(R.font.ruwudu_500, w(500)), Font(R.font.ruwudu_600, w(600)), Font(R.font.ruwudu_700, w(700)))
    val Sora = FontFamily(Font(R.font.sora_200, w(200)), Font(R.font.sora_300, w(300)), Font(R.font.sora_400, w(400)), Font(R.font.sora_500, w(500)), Font(R.font.sora_600, w(600)))
    val WorkSans = FontFamily(Font(R.font.worksans_400, w(400)), Font(R.font.worksans_500, w(500)), Font(R.font.worksans_600, w(600)))
}
