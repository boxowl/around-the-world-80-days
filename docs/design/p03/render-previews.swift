import AppKit

let out = URL(fileURLWithPath: CommandLine.arguments[1])
try FileManager.default.createDirectory(at: out, withIntermediateDirectories: true)
func c(_ hex: Int, _ a: CGFloat = 1) -> NSColor { NSColor(calibratedRed: CGFloat((hex >> 16)&255)/255, green: CGFloat((hex >> 8)&255)/255, blue: CGFloat(hex&255)/255, alpha: a) }
func rect(_ x: CGFloat,_ y: CGFloat,_ w: CGFloat,_ h: CGFloat,_ color: NSColor,_ radius: CGFloat = 0) { color.setFill(); NSBezierPath(roundedRect:NSRect(x:x,y:y,width:w,height:h),xRadius:radius,yRadius:radius).fill() }
func line(_ pts:[NSPoint],_ color:NSColor,_ width:CGFloat = 1) { color.setStroke(); let p=NSBezierPath(); p.lineWidth=width; p.move(to:pts[0]); for q in pts.dropFirst(){p.line(to:q)}; p.stroke() }
func poly(_ pts:[NSPoint],_ color:NSColor) { color.setFill(); let p=NSBezierPath(); p.move(to:pts[0]); for q in pts.dropFirst(){p.line(to:q)}; p.close(); p.fill() }
func dot(_ x:CGFloat,_ y:CGFloat,_ r:CGFloat,_ color:NSColor) {color.setFill(); NSBezierPath(ovalIn:NSRect(x:x-r,y:y-r,width:2*r,height:2*r)).fill()}
func txt(_ s:String,_ x:CGFloat,_ y:CGFloat,_ w:CGFloat,_ size:CGFloat,_ color:NSColor,_ bold:Bool=false,_ serif:Bool=false) { let font = serif ? (NSFont(name:"Georgia-Bold",size:size) ?? NSFont.systemFont(ofSize:size,weight:.bold)) : NSFont.systemFont(ofSize:size,weight:bold ? .bold : .regular); let p=NSMutableParagraphStyle(); p.lineBreakMode = .byWordWrapping; let a:[NSAttributedString.Key:Any]=[.font:font,.foregroundColor:color,.paragraphStyle:p]; NSGraphicsContext.saveGraphicsState();let h=size*2.7;let tr=NSAffineTransform();tr.translateX(by:0,yBy:2*y+h);tr.scaleX(by:1,yBy:-1);tr.concat();(s as NSString).draw(with:NSRect(x:x,y:y,width:w,height:h),options:[.usesLineFragmentOrigin,.usesFontLeading],attributes:a);NSGraphicsContext.restoreGraphicsState() }
struct Theme {let name:String; let paper:Int;let ink:Int;let accent:Int;let muted:Int;let card:Int;let nav:Int;let sky:Int;let sky2:Int;let building:Int;let road:Int;let caption:Int;let captionInk:Int;let flat:Bool;let round:CGFloat}
let themes = [
 Theme(name:"book",paper:0xf4ebd8,ink:0x142e3a,accent:0xa4412c,muted:0x334c56,card:0xfffaf0,nav:0xf6efdf,sky:0x8fa3b6,sky2:0xd8c7a7,building:0x4f6674,road:0x253e4e,caption:0xfff6e6,captionInk:0x173543,flat:false,round:4),
 Theme(name:"poster",paper:0xf2dfbd,ink:0x193949,accent:0xa63c25,muted:0x294654,card:0xfbe9c8,nav:0x173e50,sky:0xd06d43,sky2:0xf0b16b,building:0x183c4c,road:0x122f40,caption:0x173e50,captionInk:0xfff3d8,flat:true,round:0),
 Theme(name:"diorama",paper:0xeaf0e8,ink:0x183b40,accent:0xa24830,muted:0x35545a,card:0xfffdf4,nav:0xfbfff8,sky:0x94b8ba,sky2:0xd3d1ae,building:0x556a6c,road:0x334e54,caption:0xf9fff1,captionInk:0x193e40,flat:false,round:15)
]
func scene(_ t:Theme,_ x:CGFloat,_ y:CGFloat,_ w:CGFloat,_ h:CGFloat) {
 let s=w/360; let k=h/250
 func X(_ v:CGFloat)->CGFloat{x+v*s}; func Y(_ v:CGFloat)->CGFloat{y+v*k}
 rect(x,y,w,h,c(t.sky))
 for i in 0..<30 {rect(x,y+CGFloat(i)*h/30,w,h/30+1,c(t.sky2,CGFloat(i)/42))}
 if t.flat {dot(X(277),Y(70),52*s,c(0xf9ca81))}
 for (bx,bw,bh) in [(0,39,70),(44,27,95),(83,21,81),(318,28,90)] {rect(X(CGFloat(bx)),Y(CGFloat(145-bh)),CGFloat(bw)*s,CGFloat(bh)*k,c(t.building,0.5))}
 rect(X(90),Y(56),90*s,101*k,c(t.building)); poly([NSPoint(x:X(90),y:Y(56)),NSPoint(x:X(135),y:Y(30)),NSPoint(x:X(180),y:Y(56))],c(t.building)); rect(X(210),Y(91),92*s,67*k,c(t.building)); poly([NSPoint(x:X(207),y:Y(91)),NSPoint(x:X(256),y:Y(62)),NSPoint(x:X(305),y:Y(91))],c(t.building))
 dot(X(135),Y(73),12*s,c(0xc9b697)); dot(X(135),Y(73),9*s,c(t.building)); line([NSPoint(x:X(135),y:Y(72)),NSPoint(x:X(135),y:Y(65)),NSPoint(x:X(141),y:Y(72))],c(0xf3d5a3),1.3*s)
 for wx in [105,126,148,224,246,268] {rect(X(CGFloat(wx)),Y(102),11*s,18*k,c(t.flat ? 0xe6a865:0xd9bd88))}
 poly([NSPoint(x:X(0),y:Y(158)),NSPoint(x:X(360),y:Y(154)),NSPoint(x:X(360),y:Y(250)),NSPoint(x:X(0),y:Y(250))],c(t.road))
 for yy in [168,194,226] {line([NSPoint(x:X(0),y:Y(CGFloat(yy))),NSPoint(x:X(180),y:Y(CGFloat(yy-9))),NSPoint(x:X(360),y:Y(CGFloat(yy-2)))],c(0xd1b894,0.46),1.3*s)}
 for bx in [16,77,151,245,335] {line([NSPoint(x:X(CGFloat(bx)),y:Y(160)),NSPoint(x:X(CGFloat(bx)-35),y:Y(250))],c(0xd1b894,0.36),1*s)}
 for (lx,ly) in [(42,74),(57,74),(331,95)] {line([NSPoint(x:X(CGFloat(lx)),y:Y(CGFloat(ly))),NSPoint(x:X(CGFloat(lx)),y:Y(172))],c(0x203a43),3*s);dot(X(CGFloat(lx)),Y(CGFloat(ly)),6*s,c(0xf5d289)); rect(X(CGFloat(lx)-2),Y(176),4*s,34*k,c(0xf5d289,0.23))}
 for (sx,sy,rr) in [(210,80,12),(229,66,16),(250,51,18),(271,39,21)] {dot(X(CGFloat(sx)),Y(CGFloat(sy)),CGFloat(rr)*s,c(t.flat ? 0xf3c38d:0xe4ded1,0.58))}
 if t.name == "diorama" {rect(X(148),Y(144),174*s,32*k,c(0x193b3d,0.28),8*s)}
 rect(X(151),Y(146),169*s,25*k,c(0x1d3947),t.name == "diorama" ? 5*s:0)
 rect(X(213),Y(111),76*s,38*k,c(0x263f4b),t.name == "diorama" ? 4*s:0)
 poly([NSPoint(x:X(166),y:Y(148)),NSPoint(x:X(166),y:Y(122)),NSPoint(x:X(186),y:Y(122)),NSPoint(x:X(196),y:Y(148))],c(0x263f4b))
 for wx in [221,251] {rect(X(CGFloat(wx)),Y(117),23*s,19*k,c(0xd4ad70))}
 rect(X(166),Y(152),126*s,3*k,c(0xb96d4a))
 for cx in [181,237,283] {dot(X(CGFloat(cx)),Y(171),12*s,c(0xc9a86f));dot(X(CGFloat(cx)),Y(171),8*s,c(0x142f39))}
 line([NSPoint(x:X(145),y:Y(175)),NSPoint(x:X(330),y:Y(175))],c(0xd5b581),3*s)
 if t.name == "book" {for i in 0..<700 {let a=(i*53)%360;let b=(i*97)%250;dot(X(CGFloat(a)),Y(CGFloat(b)),0.35*s,c(i%2==0 ? 0xffffff:0x173543,0.14))}; rect(x+3,y+3,w-6,h-6,c(0xf4ebd8,0.1))}
 if t.flat {rect(x,y+2,w,4*k,c(0xf3d9ab));rect(x,y+h-5*k,w,4*k,c(0xf3d9ab))}
}
func phone(_ t:Theme,_ w:Int,_ h:Int,_ large:Bool=false) throws {
 let scale=2; let W=CGFloat(w),H=CGFloat(h); let rep=NSBitmapImageRep(bitmapDataPlanes:nil,pixelsWide:w*scale,pixelsHigh:h*scale,bitsPerSample:8,samplesPerPixel:4,hasAlpha:true,isPlanar:false,colorSpaceName:.deviceRGB,bytesPerRow:0,bitsPerPixel:0)!; rep.size=NSSize(width:W,height:H)
 NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current=NSGraphicsContext(bitmapImageRep:rep); let tr=NSAffineTransform();tr.translateX(by:0,yBy:H);tr.scaleX(by:1,yBy:-1);tr.concat()
 rect(0,0,W,H,c(t.paper),24)
 txt("9:41",20,5,70,11,c(t.ink),true);txt("▰ ▰ ▰",W-67,5,60,11,c(t.ink),true)
 txt("Вокруг света\nза 80 дней",17,30,185,large ? 23:18,c(t.ink),true,true)
 rect(W-111,36,98,25,c(t.ink),12);txt("ДЕНЬ 01 · ДЕМО",W-105,43,90,10,c(0xffffff),true)
 let sceneY:CGFloat=large ? 84:75;let sceneH:CGFloat=large ? (h==640 ? 125:250):(h==640 ? 232:340)
 scene(t,0,sceneY,W,sceneH)
 rect(14,sceneY+sceneH-34,175,25,c(t.caption),t.name=="diorama" ? 12:2);txt("Лондон · отправление",22,sceneY+sceneH-29,165,12,c(t.captionInk),true)
 let base=sceneY+sceneH+12; rect(15,base,43,17,c(t.ink),3);txt("ДЕМО",20,base+3,37,10,c(0xffffff),true);txt("ПЕРВАЯ ГЛАВА",66,base+3,150,11,c(t.accent),true)
 txt("Лондон → Дувр",15,base+25,W-30,large ? 36:(h==640 ? 25:31),c(t.ink),true,true)
 let progressY=base+(large ? 74:63);rect(15,progressY,W-30,6,c(0xd8c9af),3);rect(15,progressY,(W-30)*0.56,6,c(t.accent),3)
 let metricY=progressY+18;let mW=large ? W-30:(W-39)/2;let mH:CGFloat=large ? 62:(h==640 ? 67:93)
 for i in 0..<2 {let mx=large ? 15:15+CGFloat(i)*(mW+9);let my=metricY+(large ? CGFloat(i)*70:0);rect(mx,my,mW,mH,c(t.card),t.round);line([NSPoint(x:mx,y:my),NSPoint(x:mx+mW,y:my)],c(t.ink,0.32),1);txt(i==0 ? "Шаги сегодня · демо":"До остановки · демо",mx+10,my+7,mW-20,large ? 14:11,c(t.muted),true);txt(i==0 ? "2 240":"1 760",mx+10,my+(large ? 28:30),mW-20,large ? 26:22,c(t.ink),true)}
 txt("Следующая цель: белые скалы Дувра",15,metricY+mH+(large ? 78:8),W-30,large ? 16:12,c(t.muted),true)
 let navH:CGFloat=large ? 91:(h==640 ? 60:74);let navY=H-navH;rect(0,navY,W,navH,c(t.nav));line([NSPoint(x:0,y:navY),NSPoint(x:W,y:navY)],c(t.ink,0.24),1)
 let labels=["Путешествие","Карта","Дневник","Паспорт"];let symbols=["✧","⌘","▤","◉"]
 for i in 0..<4 {let nx=CGFloat(i)*W/4;let selected=i==0;let nc = t.name=="poster" ? c(selected ? 0xffcd91:0xe5e7dc):c(selected ? t.accent:t.muted);txt(symbols[i],nx,navY+6,W/4,22,nc,true);txt(large && i==0 ? "Путеше-\nствие":labels[i],nx+3,navY+34,W/4-6,large ? 12:10,nc,true)}
 NSGraphicsContext.current?.flushGraphics();NSGraphicsContext.restoreGraphicsState()
 let suffix=large ? "-large":"";let url=out.appendingPathComponent("\(t.name)-\(w)x\(h)\(suffix).png");try rep.representation(using:.png,properties:[:])!.write(to:url)
}
for t in themes {try phone(t,360,640);try phone(t,412,915);try phone(t,360,640,true)}
