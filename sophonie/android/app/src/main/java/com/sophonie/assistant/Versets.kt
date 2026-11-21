package com.sophonie.assistant

import java.util.Calendar

/**
 * Versets et prières intégrés, pour le mode sans clé (hors ligne).
 * Textes : Bible Louis Segond 1910 (domaine public).
 */
object Versets {
    data class Verset(val reference: String, val texte: String)

    private val matin = listOf(
        Verset("Psaume 5:4", "Éternel ! le matin tu entends ma voix ; le matin je me tourne vers toi, et je regarde."),
        Verset("Psaume 118:24", "C'est ici la journée que l'Éternel a faite : qu'elle soit pour nous un sujet d'allégresse et de joie !"),
        Verset("Lamentations 3:22-23", "Les bontés de l'Éternel ne sont pas épuisées, ses compassions ne sont pas à leur terme ; elles se renouvellent chaque matin. Oh ! que ta fidélité est grande !"),
        Verset("Psaume 23:1", "L'Éternel est mon berger : je ne manquerai de rien."),
        Verset("Psaume 27:1", "L'Éternel est ma lumière et mon salut : de qui aurais-je crainte ? L'Éternel est le soutien de ma vie : de qui aurais-je peur ?"),
        Verset("Proverbes 3:5-6", "Confie-toi en l'Éternel de tout ton cœur, et ne t'appuie pas sur ta sagesse ; reconnais-le dans toutes tes voies, et il aplanira tes sentiers."),
        Verset("Proverbes 16:3", "Recommande à l'Éternel tes œuvres, et tes projets réussiront."),
        Verset("Psaume 37:5", "Recommande ton sort à l'Éternel, mets en lui ta confiance, et il agira."),
        Verset("Ésaïe 40:31", "Mais ceux qui se confient en l'Éternel renouvellent leur force. Ils prennent le vol comme les aigles ; ils courent, et ne se lassent point, ils marchent, et ne se fatiguent point."),
        Verset("Ésaïe 41:10", "Ne crains rien, car je suis avec toi ; ne promène pas des regards inquiets, car je suis ton Dieu ; je te fortifie, je viens à ton secours, je te soutiens de ma droite triomphante."),
        Verset("Josué 1:9", "Ne t'ai-je pas donné cet ordre : Fortifie-toi et prends courage ? Ne t'effraie point et ne t'épouvante point, car l'Éternel, ton Dieu, est avec toi dans tout ce que tu entreprendras."),
        Verset("Jérémie 29:11", "Car je connais les projets que j'ai formés sur vous, dit l'Éternel, projets de paix et non de malheur, afin de vous donner un avenir et de l'espérance."),
        Verset("Matthieu 6:33", "Cherchez premièrement le royaume et la justice de Dieu ; et toutes ces choses vous seront données par-dessus."),
        Verset("Philippiens 4:13", "Je puis tout par celui qui me fortifie."),
        Verset("Psaume 119:105", "Ta parole est une lampe à mes pieds, et une lumière sur mon sentier."),
        Verset("Psaume 121:1-2", "Je lève mes yeux vers les montagnes… D'où me viendra le secours ? Le secours me vient de l'Éternel, qui a fait les cieux et la terre."),
        Verset("Romains 12:12", "Réjouissez-vous en espérance. Soyez patients dans l'affliction. Persévérez dans la prière."),
        Verset("1 Thessaloniciens 5:16-18", "Soyez toujours joyeux. Priez sans cesse. Rendez grâces en toutes choses, car c'est à votre égard la volonté de Dieu en Jésus-Christ."),
    )

    private val soir = listOf(
        Verset("Psaume 4:9", "Je me couche et je m'endors en paix, car toi seul, ô Éternel ! tu me donnes la sécurité dans ma demeure."),
        Verset("Psaume 3:6", "Je me couche, et je m'endors ; je me réveille, car l'Éternel est mon soutien."),
        Verset("Psaume 141:2", "Que ma prière soit devant ta face comme l'encens, et l'élévation de mes mains comme l'offrande du soir !"),
        Verset("Psaume 91:1", "Celui qui demeure sous l'abri du Très-Haut repose à l'ombre du Tout-Puissant."),
        Verset("Psaume 121:8", "L'Éternel gardera ton départ et ton arrivée, dès maintenant et à jamais."),
        Verset("Matthieu 11:28", "Venez à moi, vous tous qui êtes fatigués et chargés, et je vous donnerai du repos."),
        Verset("Jean 14:27", "Je vous laisse la paix, je vous donne ma paix. Je ne vous donne pas comme le monde donne. Que votre cœur ne se trouble point, et ne s'alarme point."),
        Verset("Philippiens 4:6-7", "Ne vous inquiétez de rien ; mais en toute chose faites connaître vos besoins à Dieu par des prières et des supplications, avec des actions de grâces. Et la paix de Dieu, qui surpasse toute intelligence, gardera vos cœurs et vos pensées en Jésus-Christ."),
        Verset("1 Pierre 5:7", "Et déchargez-vous sur lui de tous vos soucis, car lui-même prend soin de vous."),
        Verset("Matthieu 6:34", "Ne vous inquiétez donc pas du lendemain ; car le lendemain aura soin de lui-même. À chaque jour suffit sa peine."),
        Verset("Romains 8:28", "Nous savons, du reste, que toutes choses concourent au bien de ceux qui aiment Dieu, de ceux qui sont appelés selon son dessein."),
        Verset("Psaume 46:2", "Dieu est pour nous un refuge et un appui, un secours qui ne manque jamais dans la détresse."),
        Verset("Nombres 6:24-26", "Que l'Éternel te bénisse, et qu'il te garde ! Que l'Éternel fasse luire sa face sur toi, et qu'il t'accorde sa grâce ! Que l'Éternel tourne sa face vers toi, et qu'il te donne la paix !"),
        Verset("Psaume 30:6", "Car sa colère dure un instant, mais sa grâce toute la vie ; le soir arrivent les pleurs, et le matin l'allégresse."),
    )

    private val prieresMatin = listOf(
        "Seigneur, merci pour cette nouvelle journée. Guide mes pas, mes paroles et mes décisions. Que tout ce que je ferai aujourd'hui t'honore.",
        "Père, je te confie cette journée : mon travail, ma famille et chaque rencontre. Donne-moi la sagesse, la patience et la paix.",
        "Seigneur, remplis-moi de ta force ce matin. Éloigne de moi la crainte et l'inquiétude, et rends-moi attentif à ceux que je croiserai.",
        "Merci, Seigneur, pour le repos de la nuit. Bénis mes projets d'aujourd'hui et aide-moi à ne rien oublier de l'essentiel.",
    )

    private val prieresSoir = listOf(
        "Merci, Seigneur, pour cette journée et pour tout ce que tu m'as donné. Pardonne mes fautes, garde ma famille et accorde-moi un sommeil paisible.",
        "Père, je dépose devant toi mes soucis de la journée. Je te confie demain. Veille sur moi et sur ceux que j'aime cette nuit.",
        "Seigneur, merci pour ta présence à chaque heure. Apaise mon cœur et mon esprit, et renouvelle mes forces pour demain.",
        "Merci, Père, pour tes bontés de ce jour. Que ta paix garde ma maison cette nuit, et que je me réveille plein d'espérance.",
    )

    private fun jour(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

    fun duJour(duMatin: Boolean): Verset = (if (duMatin) matin else soir).let { it[jour() % it.size] }

    fun priere(duMatin: Boolean): String = (if (duMatin) prieresMatin else prieresSoir).let { it[jour() % it.size] }

    /** Message complet de prière (verset + prière), pour les notifications du mode sans clé. */
    fun message(duMatin: Boolean): String {
        val v = duJour(duMatin)
        return "« ${v.texte} »\n— ${v.reference}\n\n🙏 ${priere(duMatin)}"
    }
}
