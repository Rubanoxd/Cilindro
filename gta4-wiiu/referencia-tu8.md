# Referencia TU8: desensamblado de funciones clave (LibertyRecomp, OZORDI)

Origen: comentarios de `glue/rexglue-sdk-main/gta4-recomp/generated/gta4_recomp.*.cpp`.
Direcciones de **TU8**, NO de nuestro XEX. `; = X` es la constante que se forma; `; [X]` la dirección global a la que se accede.
Para encontrar el equivalente en nuestro XEX: `python3 gta4-wiiu/herramientas/busca_tu8.py --imagen out/imagen.bin <dir>`.

## sub_82145770 — Cierre de la pantalla de carga (activa/lista=0, quita diccionario, restaura delegado de render)

```
    mflr r12
    bl 0x829ff7cc
    stwu r1,-112(r1)
    clrlwi r11,r3,24
    cmplwi cr6,r11,0
    lis r11,-31971
    addi r30,r11,19908   ; = 831D4DC4
    bne cr6,0x8214579c
    lbz r11,1393(r30)   ; [831D5335]
    cmplwi cr6,r11,0
    beq cr6,0x82145814
loc_8214579C:
    lis r11,-32077
    addi r31,r11,-2080   ; = 82B2F7E0
    li r11,0
    mr r3,r31
    stb r11,1394(r30)   ; [831D5336]
    bl 0x821b7250
    mr r3,r31
    bl 0x821b5a68
    li r3,-1
    bl 0x82287f08
    lis r11,-32244
    addi r3,r11,-27752   ; = 820B9398
    bl 0x8235c370
    bl 0x82146690
    lis r31,-31971
    mr r29,r3
    lbz r11,21321(r31)   ; [831D5349]
    cmplwi cr6,r11,0
    bne cr6,0x821457ec
    bl 0x82146600
loc_821457EC:
    mr r3,r29
    bl 0x821464b8
    li r11,0
    lbz r10,21321(r31)   ; [831D5349]
    cmplwi cr6,r10,0
    stw r11,0(r30)   ; [831D4DC4]
    beq cr6,0x8214580c
    bl 0x8224dbe0
loc_8214580C:
    li r11,0
    stb r11,1393(r30)   ; [831D5335]
loc_82145814:
    addi r1,r1,112
    b 0x829ff81c
```

## sub_82145420 — Arranque de la pantalla de carga (instala el pintor 82143DC0→82144188 como delegado)

```
    mflr r12
    bl 0x829ff7c8
    stwu r1,-144(r1)
    lis r11,-31971
    mr r30,r3
    addi r31,r11,21304   ; = 831D5338
    mr r29,r4
    lbz r11,-3(r31)   ; [831D5335]
    cmplwi cr6,r11,0
    bne cr6,0x821455a4
    clrlwi r28,r30,24
    cmplwi cr6,r28,0
    beq cr6,0x82145458
    bl 0x821449b8
loc_82145458:
    lis r11,-31971
    clrlwi r10,r29,24
    cmplwi cr6,r10,0
    stb r29,21323(r11)   ; [831D534B]
    beq cr6,0x82145478
    cmplwi cr6,r28,0
    li r11,1
    beq cr6,0x8214547c
loc_82145478:
    li r11,0
loc_8214547C:
    stb r11,-2(r31)   ; [831D5336]
    lis r11,-32244
    lis r10,-31971
    lis r29,-31971
    lis r28,-32077
    lfs f0,-5924(r11)   ; [820BE8DC]
    li r11,0
    stfs f0,0(r31)   ; [831D5338]
    stb r30,21320(r29)   ; [831D5348]
    stw r11,21312(r10)   ; [831D5340]
    lis r10,-31971
    stw r11,21316(r10)   ; [831D5344]
    lis r10,-31971
    stb r11,21303(r10)   ; [831D5337]
    li r11,1
    lis r10,-31971
    stb r11,21322(r10)   ; [831D534A]
    lis r11,-31971
    stb r30,21321(r11)   ; [831D5349]
    li r11,1
    stb r11,-32044(r28)   ; [82B282D4]
loc_821454D0:
    mftb r9
    rotlwi r11,r9,0
    cmplwi cr6,r11,0
    beq cr6,0x821454d0
    lis r10,-31971
    lis r11,-32236
    li r7,0
    li r6,0
    addi r5,r11,15808   ; = 82143DC0
    li r4,0
    std r9,21648(r10)   ; [831D5490]
    addi r3,r1,80
    bl 0x8284d220
    lis r11,-32236
    lis r10,-32077
    ld r4,80(r1)
    addi r11,r11,14840   ; = 821439F8
    addi r30,r10,-2080   ; = 82B2F7E0
    mr r3,r30
    stw r11,92(r1)
    ld r5,88(r1)
    bl 0x821b7048
    mr r3,r30
    bl 0x821b5a68
    lis r11,-32244
    addi r3,r11,-27752   ; = 820B9398
    bl 0x82146448
    li r11,1
    stb r11,-2(r31)   ; [831D5336]
    bl 0x821455b0
    clrlwi r11,r3,24
    cmplwi cr6,r11,0
    beq cr6,0x82145594
    bl 0x82145968
    lbz r11,21320(r29)   ; [831D5348]
    cmplwi cr6,r11,0
    bne cr6,0x82145578
    li r4,1
    li r3,1
    bl 0x82144708
    li r11,0
    stb r11,-32044(r28)   ; [82B282D4]
loc_82145578:
    bl 0x821458e0
    li r11,1
    stb r11,-3(r31)   ; [831D5335]
    li r11,0
    stb r11,-2(r31)   ; [831D5336]
    addi r1,r1,144
    b 0x829ff818
loc_82145594:
    li r3,1
    bl 0x82145770
    li r11,0
    stb r11,-2(r31)   ; [831D5336]
loc_821455A4:
    addi r1,r1,144
    b 0x829ff818
```

## sub_82145820 — (Re)instala el pintor de la carga en el hilo de render

```
    mflr r12
    stw r12,-8(r1)
    std r31,-16(r1)
    stwu r1,-112(r1)
loc_82145830:
    mftb r9
    rotlwi r11,r9,0
    cmplwi cr6,r11,0
    beq cr6,0x82145830
    lis r10,-31971
    lis r11,-32236
    li r7,0
    li r6,0
    addi r5,r11,15808   ; = 82143DC0
    li r4,0
    std r9,21648(r10)   ; [831D5490]
    addi r3,r1,80
    bl 0x8284d220
    lis r11,-32236
    lis r10,-32077
    ld r4,80(r1)
    addi r11,r11,14840   ; = 821439F8
    addi r31,r10,-2080   ; = 82B2F7E0
    mr r3,r31
    stw r11,92(r1)
    ld r5,88(r1)
    bl 0x821b7048
    mr r3,r31
    bl 0x821b5a68
    addi r1,r1,112
    lwz r12,-8(r1)
    mtlr r12
    ld r31,-16(r1)
    blr
```

## sub_821B7048 — RenderThread::SetRenderFunction(obj=82B2F7E0, delegado de 16 B en obj+4048)

```
    mflr r12
    stw r12,-8(r1)
    std r30,-24(r1)
    std r31,-16(r1)
    stwu r1,-160(r1)
    lis r11,-32077
    std r4,192(r1)
    mr r31,r3
    std r5,200(r1)
    addi r4,r11,6016   ; = 82B31780
    addi r3,r1,88
    bl 0x8285ff50
    addi r31,r31,4048
    addi r4,r1,192
    mr r3,r31
    bl 0x8284d2a0
    clrlwi r11,r3,24
    cmplwi cr6,r11,0
    lis r11,-32077
    addi r30,r11,-2080   ; = 82B2F7E0
    bne cr6,0x821b7104
    lis r11,-32229
    li r7,4
    addi r11,r11,23696   ; = 821B5C90
    addi r6,r1,80
    li r5,0
    mr r4,r30
    addi r3,r1,96
    stw r11,80(r1)
    bl 0x8284d220
    lis r11,-32222
    addi r4,r1,96
    addi r11,r11,-30472   ; = 822188F8
    mr r3,r31
    stw r11,108(r1)
    bl 0x8284d2a0
    clrlwi r11,r3,24
    cmplwi cr6,r11,0
    beq cr6,0x821b7104
    bl 0x8225cf80
    cmplwi cr6,r3,0
    beq cr6,0x821b7100
    lwz r11,544(r3)
    lwz r10,1200(r11)
    rlwinm r10,r10,0,23,21
    stw r10,1200(r11)
loc_821B7100:
    bl 0x8227faa0
loc_821B7104:
    lis r10,-32229
    addi r11,r1,192
    addi r10,r10,23696   ; = 821B5C90
    li r7,4
    addi r6,r1,84
    li r5,0
    lwz r9,4(r11)
    mr r4,r30
    stw r10,84(r1)
    addi r3,r1,112
    lwz r10,0(r11)
    lwz r8,8(r11)
    lwz r11,12(r11)
    stw r9,4(r31)
    stw r10,0(r31)
    stw r8,8(r31)
    stw r11,12(r31)
    bl 0x8284d220
    lis r11,-32222
    addi r4,r1,112
    addi r11,r11,-30472   ; = 822188F8
    mr r3,r31
    stw r11,124(r1)
    bl 0x8284d2a0
    clrlwi r11,r3,24
    cmplwi cr6,r11,0
    beq cr6,0x821b718c
    bl 0x8225cf80
    cmplwi cr6,r3,0
    beq cr6,0x821b718c
    lwz r11,544(r3)
    lwz r10,1200(r11)
    ori r10,r10,512
    stw r10,1200(r11)
loc_821B718C:
    addi r3,r1,88
    bl 0x8285ffa0
    addi r1,r1,160
    lwz r12,-8(r1)
    mtlr r12
    ld r30,-24(r1)
    ld r31,-16(r1)
    blr
```

## sub_821B7250 — RenderThread::RestoreDefaultRenderFunction (delegado por defecto = 821B5C90)

```
    mflr r12
    bl 0x829ff7cc
    stwu r1,-160(r1)
    lis r11,-32077
    mr r30,r3
    addi r29,r11,-2080   ; = 82B2F7E0
    lis r11,-32229
    li r7,4
    addi r11,r11,23696   ; = 821B5C90
    addi r6,r1,80
    li r5,0
    mr r4,r29
    addi r3,r1,112
    stw r11,80(r1)
    bl 0x8284d220
    lis r11,-32222
    addi r4,r1,112
    addi r11,r11,-30472   ; = 822188F8
    addi r3,r30,4064
    stw r11,124(r1)
    bl 0x8284d2a0
    clrlwi r11,r3,24
    cmplwi cr6,r11,0
    bne cr6,0x821b7300
loc_821B72B0:
    mftb r9
    rotlwi r11,r9,0
    cmplwi cr6,r11,0
    beq cr6,0x821b72b0
    lis r11,-32256
    lis r31,-32077
    lis r10,-32077
    lfs f0,2612(r11)   ; [82000A34]
    lis r11,-32077
    std r9,6008(r10)   ; [82B31778]
    stfs f0,-2132(r11)   ; [82B2F7AC]
    lbz r11,-2135(r31)   ; [82B2F7A9]
    cmplwi cr6,r11,0
    beq cr6,0x821b7300
    lis r11,-32067
    li r4,0
    addi r3,r11,-15880   ; = 82BCC1F8
    bl 0x82216ae8
    li r11,0
    stb r11,-2135(r31)   ; [82B2F7A9]
loc_821B7300:
    lis r11,-32229
    li r7,4
    addi r11,r11,23696   ; = 821B5C90
    addi r6,r1,84
    li r5,0
    mr r4,r29
    addi r3,r1,96
    stw r11,84(r1)
    bl 0x8284d220
    lis r11,-32222
    mr r3,r30
    ld r4,96(r1)
    addi r11,r11,-30472   ; = 822188F8
    stw r11,108(r1)
    ld r5,104(r1)
    bl 0x821b7048
    addi r1,r1,160
    b 0x829ff81c
```

## sub_821458B8 — Getter: activa ? audiogate : 0

```
    lis r11,-31971
    lbz r11,21301(r11)   ; [831D5335]
    cmplwi cr6,r11,0
    beq cr6,0x821458d4
    lis r11,-31971
    lbz r3,21320(r11)   ; [831D5348]
    blr
loc_821458D4:
    li r3,0
    blr
```

## sub_821458A8 — Getter: activa

```
    lis r11,-31971
    lbz r3,21301(r11)   ; [831D5335]
    blr
```

## sub_828D9768 — Alias A8R8G8B8 (D3DFMT 0x18280186) sobre la memoria de un resolve de profundidad

```
    mflr r12
    bl 0x829ff7c8
    stwu r1,-160(r1)
    mr r30,r4
    mr r31,r3
    mr r3,r30
    lwz r11,0(r30)
    lwz r11,24(r11)
    mtctr r11
    bctrl
    mr r11,r3
    mr r3,r30
    sth r11,28(r31)
    lwz r11,0(r30)
    lwz r11,28(r11)
    mtctr r11
    bctrl
    mr r11,r3
    li r28,0
    li r3,52
    sth r11,30(r31)
    stw r28,32(r31)
    bl 0x821b3510
    lhz r11,28(r31)
    mr r29,r3
    stw r28,108(r1)
    lis r7,6184
    stw r28,100(r1)
    rotlwi r28,r11,2
    li r10,0
    lhz r4,30(r31)
    li r9,0
    li r8,0
    stw r29,92(r1)
    ori r7,r7,390   ; = 18280186
    li r6,0
    stw r28,84(r1)
    li r5,1
    mr r3,r11
    bl 0x82a55738
    lwz r11,0(r30)
    mr r3,r30
    lwz r11,64(r11)
    mtctr r11
    bctrl
    mr r11,r3
    li r5,0
    mr r3,r29
    lwz r11,32(r11)
    rlwinm r4,r11,0,0,19
    bl 0x82a55a08
    li r3,1
    stw r29,24(r31)
    addi r1,r1,160
    b 0x829ff818
```

## sub_82598EB0 — Nativo: densidad de coches (aleatorios y aparcados) → 82AA1888/82AA188C

```
    lwz r11,8(r3)
    lfs f0,0(r11)
    lis r11,-32086
    stfs f0,6280(r11)   ; [82AA1888]
    lis r11,-32086
    stfs f0,6284(r11)   ; [82AA188C]
    blr
```

## sub_82598ED0 — Nativo: densidad de coches aleatorios

```
    lwz r10,8(r3)
    lis r11,-32086
    lfs f0,0(r10)
    stfs f0,6280(r11)   ; [82AA1888]
    blr
```

## sub_82598EE8 — Nativo: densidad de coches aparcados

```
    lwz r10,8(r3)
    lis r11,-32086
    lfs f0,0(r10)
    stfs f0,6284(r11)   ; [82AA188C]
    blr
```

## sub_823F2AB0 — Setter: densidad de peatones → 82AA2940

```
    lis r11,-32086
    stfs f1,10560(r11)   ; [82AA2940]
    blr
```

## sub_823F2AC0 — Setter: densidad de peatones de escenario → 82AA2944/82AA2948

```
    lis r11,-32086
    stfs f1,10564(r11)   ; [82AA2944]
    lis r11,-32086
    stfs f2,10568(r11)   ; [82AA2948]
    blr
```

## sub_8247E4C0 — Límite de referencias a drawables (r4)

```
    li r4,13000
    b 0x8247e3b8
```

## sub_824F3418 — Selección de LOD de un drawable

```
    li r11,0
    lis r10,-32256
    stw r11,0(r5)
    lfs f12,2612(r10)   ; [82000A34]
    stw r11,0(r6)
    stfs f12,0(r7)
    lwz r10,68(r4)
    cmpwi cr6,r10,0
    beqlr cr6
    lis r10,-31999
    lbz r10,27168(r10)   ; [83016A20]
    cmplwi cr6,r10,0
    beq cr6,0x824f3458
    stw r11,0(r5)
    stw r11,0(r6)
    blr
loc_824F3458:
    lis r10,-32087
    lfs f13,80(r4)
    li r9,1
    lfs f11,84(r4)
    lfs f0,12724(r10)   ; [82A931B4]
    fmuls f13,f13,f0
    stw r9,0(r5)
    fmuls f0,f11,f0
    fcmpu cr6,f1,f13
    bge cr6,0x824f3488
    stw r11,0(r5)
    b 0x824f34cc
loc_824F3488:
    fcmpu cr6,f1,f0
    ble cr6,0x824f34b8
    li r10,2
    stw r10,0(r5)
    lwz r10,72(r4)
    cmpwi cr6,r10,0
    bne cr6,0x824f34cc
    stw r9,0(r5)
    stw r9,0(r6)
    lwz r10,0(r5)
    cmpwi cr6,r10,1
    bne cr6,0x824f34cc
loc_824F34B8:
    lwz r10,68(r4)
    cmpwi cr6,r10,0
    bne cr6,0x824f34cc
    stw r11,0(r5)
    stw r11,0(r6)
loc_824F34CC:
    lwz r10,0(r6)
    addi r10,r10,16
    rlwinm r10,r10,2,0,29
    lwzx r10,r10,r4
    cmpwi cr6,r10,0
    bnelr cr6
    stw r11,0(r6)
    stfs f12,0(r7)
    blr
```

## sub_8284E5C0 — InterlockedExchange (corchete mfmsr/mtmsrd r13,1)

```
loc_8284E5C0:
    mfmsr r10
    mtmsrd r13,1
    lwarx r11,0,r3
    stwcx. r4,0,r3
    mtmsrd r10,1
    bne 0x8284e5c0
    mr r3,r11
    blr
```

## sub_8284E5E0 — InterlockedCompareExchange

```
loc_8284E5E0:
    mfmsr r10
    mtmsrd r13,1
    lwarx r11,0,r3
    cmpw cr6,r11,r5
    bne cr6,0x8284e608
    stwcx. r4,0,r3
    mtmsrd r10,1
    bne 0x8284e5e0
    mr r3,r11
    blr
loc_8284E608:
    stwcx. r11,0,r3
    mtmsrd r10,1
    mr r3,r11
    blr
```

## sub_8284E618 — InterlockedIncrement

```
loc_8284E618:
    mfmsr r10
    mtmsrd r13,1
    lwarx r11,0,r3
    addi r11,r11,1
    stwcx. r11,0,r3
    mtmsrd r10,1
    bne 0x8284e618
    mr r3,r11
    blr
```

## sub_8284E640 — InterlockedDecrement

```
loc_8284E640:
    mfmsr r10
    mtmsrd r13,1
    lwarx r11,0,r3
    addi r11,r11,-1
    stwcx. r11,0,r3
    mtmsrd r10,1
    bne 0x8284e640
    mr r3,r11
    blr
```

## sub_8284E668 — InterlockedExchangeAdd

```
loc_8284E668:
    mfmsr r9
    mtmsrd r13,1
    lwarx r11,0,r3
    add r10,r4,r11
    stwcx. r10,0,r3
    mtmsrd r9,1
    bne 0x8284e668
    mr r3,r11
    blr
```

