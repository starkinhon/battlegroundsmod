"""The four bosses of 2026-11-12 (Joy Boy, Saitama, Shigaraki, Makima): writes their souls_bosses files.
Edit the numbers here and run: python3 tools/bosses/novos_bosses.py"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', '..', 'src/main/resources/data/battlegroundsmod/souls_bosses')


def skill(slot, tipo, peso, cd, cdmax, aviso, dmin, dmax, cor, som='minecraft:entity.player.attack.sweep', giro=30, **extra):
    s = {'slot': slot, 'tipo': tipo, 'peso': peso, 'recargaTicks': cd, 'recargaMaxTicks': cdmax, 'avisoTicks': aviso,
         'distanciaMin': dmin, 'distanciaMax': dmax, 'giroGrausPorTick': giro, 'avisoCor': cor, 'avisoSom': som}
    s.update(extra)
    return s


def fase(comeca, skills, combos, agress, menos=0, vel=1.0, aviso=1.0, reacao=7, chance=0.7, pausa=12, cor='red', trans=0,
         titulo='', sub=''):
    return {'comecaEm': comeca, 'skills': skills, 'combos': combos, 'agressividade': agress, 'multRecarga': 1.0,
            'menosRecargaTicks': menos, 'multVelocidade': vel, 'multAviso': aviso, 'reacaoTicks': reacao, 'chanceReacao': chance,
            'pausaTicks': pausa, 'despertado': False, 'corBarra': cor, 'transicaoTicks': trans, 'titulo': titulo, 'subtitulo': sub}


def base(boss, poder, tier, vida, vel, titulo, sub, cor, estilo=None, ideal=5, soco=3.0, intervalo_soco=11, decisao=6,
         tecnicas=90, andar=0.9, correr=1.3, investida=(90, 7, 1.5, 0.2), sombra=(110, 0.15, 0.05, 0.35), empurrao=0.8,
         reacoes=None, ritmo=(80, 160, 60, 120, 0.8, 1.25, 60)):
    d = {'formato': 'souls-boss/1', 'boss': boss, 'poder': poder, 'tier': tier}
    if estilo:
        d['estilo'] = estilo
    d.update({
        'dropAlma': True,
        'atributos': {'vida': vida, 'armadura': 0, 'velocidade': vel, 'resistenciaEmpurrao': empurrao, 'alcanceVisao': 64, 'multDano': 1.0},
        'intro': {'duracaoTicks': 60, 'titulo': titulo, 'subtitulo': sub, 'cor': cor},
        'percepcao': {'intervaloDecisaoTicks': decisao, 'aleatoriedade': 0.2, 'trocarHumorTicks': 60, 'intervaloTecnicasTicks': tecnicas},
        'distancias': {'soco': soco, 'ideal': ideal, 'intervaloSocoTicks': intervalo_soco},
        'movimento': {'velocidadeAndar': andar, 'velocidadeCorrer': correr, 'trocarLadoTicks': 40},
        'ritmo': {'calmoMinTicks': ritmo[0], 'calmoMaxTicks': ritmo[1], 'rapidoMinTicks': ritmo[2], 'rapidoMaxTicks': ritmo[3],
                  'velocidadeCalmo': ritmo[4], 'velocidadeRapido': ritmo[5], 'recargaInvestidaRapido': ritmo[6]},
        'investida': {'recargaTicks': investida[0], 'distanciaMin': investida[1], 'forca': investida[2], 'altura': investida[3]},
        'passoSombra': {'recargaTicks': sombra[0], 'distanciaMin': 3, 'distanciaMax': 16, 'atras': 1.8, 'chance': sombra[1],
                        'chanceCalmo': sombra[2], 'chanceNaEsquiva': sombra[3]},
        'puloDuplo': {'alturaMin': 2.5, 'distanciaMax': 6},
        'teleporte': {'ativo': True, 'presoTicks': 60, 'distancia': 2},
        'reacoes': reacoes or {'chanceEsquivarSkill': 0.35, 'chanceEsquivarProjetil': 0.35, 'chanceContraAtaque': 0.45, 'chanceEsquivarUlt': 0.7,
                               'golpesParaContra': 3, 'janelaPressaoTicks': 30, 'punirTicks': 100, 'multPunir': 2, 'golpesParaFugir': 4,
                               'chanceFugirAoApanhar': 0.2},
        'ultFinal': {'ativa': False},
    })
    return d


FORTE = lambda cor='#ffffff': skill(4, 'golpe', 1.0, 240, 360, 4, 0, 4, cor, giro=20)
BARRAGEM = lambda cor='#ffffff': skill(5, 'barragem', 1.0, 280, 400, 5, 0, 3.5, cor, giro=10)


def write(d):
    with open(os.path.join(OUT, d['boss'] + '.json'), 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write('\n')


# ---------------------------------------------------------------- Joy Boy (Luffy), tier 2
# The Gear of each blow comes from JoyBoyTactics (G2 above 60%, G4 below, G5 at 25%).
RED = '#c0392b'
d = base('joy_boy', 'rubber_boy', 2, 450, 0.36, 'Joy Boy', 'O guerreiro da libertação', RED, ideal=6, intervalo_soco=10,
         decisao=5, tecnicas=70, correr=1.4, investida=(70, 6, 1.6, 0.25), sombra=(100, 0.12, 0.04, 0.35), empurrao=0.7,
         ritmo=(60, 120, 60, 120, 0.9, 1.35, 40))
d['skills'] = {
    'pistol': skill(0, 'alcance', 1.6, 40, 70, 3, 0, 16, RED),
    'gatling': skill(1, 'alcance', 1.3, 90, 140, 4, 0, 10, RED),
    'bazooka': skill(2, 'controle', 1.3, 90, 140, 4, 0, 12, RED, som='minecraft:entity.player.attack.strong'),
    'especial': skill(3, 'alcance', 1.2, 260, 360, 6, 0, 18, '#f2d16b', som='minecraft:entity.player.attack.strong'),
    'gear5': skill(7, 'roteiro', 1.0, 40, 40, 10, 0, 64, '#ffffff', som='minecraft:block.note_block.basedrum'),
    'gigante': skill(7, 'roteiro', 1.0, 40, 40, 6, 0, 64, '#ffffff', som='minecraft:entity.slime.jump'),
    'forte': FORTE(), 'barragem': BARRAGEM(),
}
d['combos'] = {
    'borracha': {'peso': 1.0, 'passos': ['soco', 'pistol', 'soco', 'soco', 'forte']},
    'chuva_de_socos': {'peso': 1.0, 'passos': ['aproximar', 'soco', 'gatling']},
    'bazooka_e_pistol': {'peso': 1.1, 'encadeado': True, 'passos': ['bazooka', 'pistol']},
    'gatling_e_especial': {'peso': 1.0, 'encadeado': True, 'passos': ['gatling', 'especial']},
}
ALL_LUFFY = ['pistol', 'gatling', 'bazooka', 'especial', 'forte', 'barragem']
d['fases'] = [
    fase(1.0, ALL_LUFFY, ['borracha', 'chuva_de_socos', 'bazooka_e_pistol'], 0.75),
    fase(0.6, ALL_LUFFY, ['borracha', 'chuva_de_socos', 'bazooka_e_pistol', 'gatling_e_especial'], 0.8, menos=20, aviso=0.9,
         reacao=6, chance=0.85, trans=40, titulo='Gear Fourth', sub='Boundman'),
    fase(0.25, ALL_LUFFY + ['gear5', 'gigante'], ['borracha', 'chuva_de_socos', 'bazooka_e_pistol', 'gatling_e_especial'], 0.85,
         menos=40, aviso=0.85, reacao=5, chance=1.0, cor='white', trans=50, titulo='Gear 5', sub='Os Tambores da Libertação'),
]
write(d)

# ---------------------------------------------------------------- Saitama, tier 1
# The serious moves share the keys with the normal ones (Opm: the Modo Serio turns each key into its serious move);
# SaitamaTactics keeps each set to its mode and gives the Modo Serio its own clock.
YEL = '#f2c94c'
d = base('saitama', 'one_punch', 1, 650, 0.3, 'Saitama', 'Um herói por hobby', YEL, estilo='deus', ideal=3, intervalo_soco=10,
         decisao=6, tecnicas=80, andar=0.7, correr=0.8, investida=(1000, 999, 0, 0), sombra=(200, 0.03, 0.02, 0.2), empurrao=1.0,
         reacoes={'chanceEsquivarSkill': 0.15, 'chanceEsquivarProjetil': 0.2, 'chanceContraAtaque': 0.6, 'chanceEsquivarUlt': 0.4,
                  'golpesParaContra': 3, 'janelaPressaoTicks': 30, 'punirTicks': 100, 'multPunir': 2, 'golpesParaFugir': 99,
                  'chanceFugirAoApanhar': 0.0},
         ritmo=(100, 200, 100, 200, 0.85, 0.95, 1000))
d['skills'] = {
    'socos': skill(0, 'golpe', 1.4, 80, 120, 4, 0, 5, YEL, giro=25),
    'gancho': skill(1, 'controle', 1.3, 110, 160, 4, 0, 5, YEL, som='minecraft:entity.player.attack.strong'),
    'chute': skill(2, 'controle', 1.2, 130, 180, 4, 0, 5, YEL, som='minecraft:entity.player.attack.strong'),
    'counter': skill(7, 'contra', 1.0, 160, 240, 0, 0, 5, YEL, som=''),
    'modo_serio': skill(3, 'roteiro', 1.0, 20, 20, 10, 0, 64, '#ffffff', som='minecraft:block.beacon.activate'),
    'soco_serio': skill(0, 'alcance', 1.0, 60, 60, 30, 0, 80, '#ffffff', som='minecraft:block.beacon.power_select', giro=12),
    'saltos': skill(1, 'alcance', 1.0, 60, 60, 24, 0, 14, '#ffffff', som='minecraft:block.beacon.power_select'),
    'mesa': skill(2, 'alcance', 1.0, 60, 60, 24, 0, 30, '#ffffff', som='minecraft:block.beacon.power_select'),
    'espirro': skill(3, 'alcance', 1.0, 60, 60, 28, 0, 50, '#ffffff', som='minecraft:block.beacon.power_select', giro=12),
    'forte': FORTE(YEL), 'barragem': BARRAGEM(YEL),
}
d['combos'] = {
    'soco_normal': {'peso': 1.0, 'passos': ['soco', 'soco', 'socos']},
    'gancho_e_chute': {'peso': 1.0, 'encadeado': True, 'passos': ['gancho', 'chute']},
    'porrada': {'peso': 0.9, 'passos': ['soco', 'soco', 'forte']},
}
NORMAL = ['socos', 'gancho', 'chute', 'counter', 'forte', 'barragem']
SERIO = ['modo_serio', 'soco_serio', 'saltos', 'mesa', 'espirro']
d['fases'] = [
    fase(1.0, NORMAL, ['soco_normal', 'gancho_e_chute', 'porrada'], 0.6, reacao=7, chance=0.7),
    fase(0.5, NORMAL + SERIO, ['soco_normal', 'gancho_e_chute', 'porrada'], 0.75, aviso=1.0, reacao=6, chance=0.9,
         cor='yellow', trans=40, titulo='Modo Sério', sub='...'),
]
write(d)

# ---------------------------------------------------------------- Shigaraki, tier 3
GRAY = '#8e1515'
d = base('shigaraki', 'shigaraki', 3, 360, 0.33, 'Tomura Shigaraki', 'Herdeiro do All For One', GRAY, ideal=7, decisao=6,
         tecnicas=80, correr=1.25, sombra=(110, 0.12, 0.05, 0.35))
d['skills'] = {
    'toque': skill(0, 'controle', 1.5, 100, 150, 4, 0, 18, GRAY, som='minecraft:entity.player.attack.strong'),
    'onda': skill(1, 'alcance', 1.2, 220, 300, 8, 0, 40, '#d9d9d9', som='minecraft:entity.warden.sonic_charge'),
    'maos': skill(2, 'controle', 1.3, 220, 300, 4, 2, 20, GRAY, som='minecraft:block.sculk.spread'),
    'tentaculos': skill(3, 'ult', 1.4, 1100, 1300, 14, 0, 22, GRAY, som='minecraft:entity.warden.emerge'),
    'absorver': skill(7, 'controle', 1.2, 500, 700, 6, 0, 18, '#7a4bd1', som='minecraft:entity.warden.sonic_charge'),
    'usar': skill(7, 'alcance', 1.0, 60, 60, 2, 0, 32, '#7a4bd1'),
    'regeneracao': skill(8, 'roteiro', 1.0, 700, 700, 4, 0, 64, '#c0ffc0', som='minecraft:entity.warden.heartbeat'),
    'fim': skill(11, 'roteiro', 1.0, 600, 600, 10, 0, 64, '#ffffff', som='minecraft:entity.warden.sonic_charge'),
    'forte': FORTE(), 'barragem': BARRAGEM(),
}
d['combos'] = {
    'maos_e_toque': {'peso': 1.4, 'encadeado': True, 'passos': ['maos', 'toque']},
    'decadencia': {'peso': 1.0, 'passos': ['soco', 'soco', 'toque']},
    'grito_e_toque': {'peso': 0.9, 'encadeado': True, 'passos': ['onda', 'toque']},
}
FIRST = ['toque', 'onda', 'maos', 'forte', 'barragem']
d['fases'] = [
    fase(1.0, FIRST, ['maos_e_toque', 'decadencia', 'grito_e_toque'], 0.6),
    fase(0.5, FIRST + ['absorver', 'usar', 'tentaculos', 'regeneracao', 'fim'], ['maos_e_toque', 'decadencia', 'grito_e_toque'], 0.75,
         menos=60, aviso=0.9, reacao=6, chance=0.9, trans=40, titulo='Sem Limites', sub='All For One'),
]
write(d)

# ---------------------------------------------------------------- Makima, tier 4
PINK = '#c9665a'
d = base('makima', 'makima', 4, 300, 0.3, 'Makima', 'Demônio do Controle', PINK, ideal=12, decisao=7, tecnicas=70, andar=0.8,
         correr=1.0, investida=(1000, 999, 0, 0), sombra=(160, 0.0, 0.0, 0.45),
         reacoes={'chanceEsquivarSkill': 0.4, 'chanceEsquivarProjetil': 0.4, 'chanceContraAtaque': 0.0, 'chanceEsquivarUlt': 0.8,
                  'golpesParaContra': 99, 'janelaPressaoTicks': 30, 'punirTicks': 100, 'multPunir': 2, 'golpesParaFugir': 2,
                  'chanceFugirAoApanhar': 0.7},
         ritmo=(120, 220, 80, 140, 0.8, 1.0, 1000))
d['skills'] = {
    'bang': skill(0, 'alcance', 1.4, 70, 110, 6, 0, 32, PINK, som='minecraft:ui.button.click'),
    'corrente': skill(1, 'controle', 1.3, 200, 280, 5, 0, 20, PINK, som='minecraft:block.chain.place'),
    'fantoche': skill(2, 'roteiro', 1.0, 60, 60, 4, 0, 64, PINK, som='minecraft:block.sculk.spread'),
    'correntes': skill(3, 'ult', 1.3, 900, 1100, 12, 0, 14, PINK, som='minecraft:entity.warden.emerge'),
    'ritual': skill(7, 'alcance', 1.2, 700, 900, 10, 0, 60, '#a3122a', som='minecraft:block.beacon.power_select'),
    'ordem': skill(9, 'controle', 1.2, 240, 320, 5, 0, 20, PINK, som='minecraft:entity.warden.sonic_charge'),
    'forte': FORTE(PINK), 'barragem': BARRAGEM(PINK),
}
d['combos'] = {
    'corrente_e_ordem': {'peso': 1.3, 'encadeado': True, 'passos': ['corrente', 'ordem']},
    'corrente_e_bang': {'peso': 1.1, 'encadeado': True, 'passos': ['corrente', 'bang']},
}
FIRST = ['bang', 'corrente', 'fantoche', 'ordem', 'forte', 'barragem']
d['fases'] = [
    fase(1.0, FIRST, ['corrente_e_ordem', 'corrente_e_bang'], 0.25, reacao=7, chance=0.8),
    fase(0.5, FIRST + ['ritual', 'correntes'], ['corrente_e_ordem', 'corrente_e_bang'], 0.3, menos=60, aviso=0.9, reacao=6,
         chance=1.0, trans=40, titulo='Demônio do Controle', sub='Morram.'),
]
write(d)
print('ok')
