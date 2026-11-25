# DronUDP

Petit programa Kotlin per controlar un **DJI Tello** per UDP. Es connecta al dron,
mostra l'estat de la bateria cada 10 segons i executa aquesta seqüència:

1. Enlairar-se (`takeoff`).
2. Dibuixar un quadrat volant: 4 vegades `forward 100` + `cw 90`.
3. Aterrar (`land`).

## Requisits

- JDK 21.
- Un DJI Tello encès.
- L'ordinador connectat a la xarxa Wi-Fi del dron (`TELLO-XXXXXX`).
- El dron ha d'estar en un espai obert i sense persones a prop.

## Execució

```bash
./gradlew run
```

En arrencar, el programa:

- Entra en mode SDK amb la comanda `command`.
- Llegeix la bateria inicial i no s'enlaira si és inferior al 15 %.
- Consulta i mostra la bateria cada 10 segons.
- Enlaira, dibuixa un quadrat d'1 metre i aterra.
- Si hi ha un error durant el vol, intenta aterrar per seguretat.

## Configuració

Les constants principals són a `Main.kt`:

| Constant | Valor | Descripció |
|---|---:|---|
| `SQUARE_SIDE_CM` | 100 | Mida de cada costat del quadrat |
| `SQUARE_SIDES` | 4 | Nombre de costats |
| `BATTERY_INTERVAL_MS` | 10000 | Interval de consulta de bateria |
| `MIN_BATTERY_PERCENT` | 15 | Bateria mínima per enlairar-se |

Les pauses de moviment també són a `Main.kt` i es poden ajustar segons el
comportament del dron.

## Estructura

```
src/main/kotlin/cat/inslaferreria/
├── Main.kt          # Seqüència de vol i monitor de bateria
└── TelloClient.kt   # Comunicació UDP amb el Tello
```

## Compilació i proves

```bash
./gradlew build
```

El test inclòs aixeca un servidor UDP local que imita el Tello, de manera que es
pot verificar la seqüència de comandes sense necessitat de dron.

## Avís

Aquest programa envia comandes reals de vol. Comprova sempre l'entorn abans
d'executar-lo i tingues el dron a la vista.
