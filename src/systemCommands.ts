import * as discord from "https://code.harmony.rocks/main";
import * as fs from "https://deno.land/std/fs/mod.ts";
import { sendError } from "./index.ts";
import { Member } from "./memberClass.ts";
import { exists, load, save, saveExport } from "./saveLoad.ts";
import { Switch } from "./switchClass.ts";
import { System } from "./systemClass.ts";

function isEmpty(string:string):boolean {
    return string == "" || string == undefined || string == null;
}

function isArrEmpty(array: Array<any>):boolean {
    return array == null || array == undefined || array == [] || array.length == 0;
}

export function createSwitch(msg: discord.Message, parsedMessage: string[]): string|void {
    if (!exists(msg.author.id,msg)) return "System does not exist.";
    let system:System = load(msg.author.id);
    parsedMessage.shift();
    let members: Member[] = [];
    if (parsedMessage[0] == "list") {
        let sw: Switch = system.switches[system.switches.length-1];
        if (!sw) return "No switches registered";
        let embed: discord.Embed  = new discord.Embed();
        embed.setTitle("Switches of " + system.name + " [`"+system.id+"`]");
        sw.addEmbedField(system,"Current fronter(s):",embed);
        let j = system.switches.length-1;
        for (let i = 0; i < system.switches.length-1; i++) {
            if (i == 10) break;
            j--;
            let currsw: Switch = system.switches[j];
            let time = Date.parse(currsw.timeStamp);
            time /= 1000;
            time = Math.floor(time);
            let str = "<t:"+time+">";
            currsw.addEmbedField(system,str,embed);
        }
        msg.channel.send({
            embeds:[embed]
        });
        return;
    }
    for (let i = 0; i < parsedMessage.length; i++) {
        let str = parsedMessage[i];
        //@ts-ignore
        let member: Member = system.memberFromName(str);
        if (!member) return "Member with name \""+str+"\" not found.";
        members.push(member);
    }
    let sw = new Switch(members);
    if (system.switches.length > 0 && system.switches[system.switches.length-1].equals(sw)) {
        return "Member(s) already fronting.";
    }
    system.switches.push(sw);
    save(msg.author.id,system);
    return "Switch registered!";
}

export function accessSystem(msg: discord.Message, parsedMessage: string[]) {
    if (exists(msg.author.id,msg)) {
        let system:System = load(msg.author.id.toString());
        let embed: discord.Embed  = new discord.Embed();

        embed.setTitle(system.name + " [`"+system.id+"`]");
        //@ts-ignore
        embed.setThumbnail(system.avatar);
        //@ts-ignore
        if (!isEmpty(system.tag)) embed.addField("Tag",system.tag,true);
        if (!isArrEmpty(system.members)) embed.addField("Members (" + system.members.length + ")","(see `pf>system list`)",true);
        //@ts-ignore
        if (!isEmpty(system.description)) embed.addField("Description",system.description,false);
        //@ts-ignore
        if (!isEmpty(system.created)) {
            //@ts-ignore
            let time:number = Date.parse(system.created);
            embed.setFooter("Created on " + new Date(time).toUTCString());
        }
        msg.channel.send({
            embeds: [embed]
        }).catch(err => {
            sendError(msg,err);
        });;
        return;
    }
    msg.channel.send("No system found. Make one with `pf>s`").catch(err => {
        sendError(msg,err);
    });;
}

export function createSystem(msg: discord.Message, parsedMessage: string[]):string {
    parsedMessage.shift();
    parsedMessage.shift();
    let name: string = parsedMessage.join(" ");
    if (exists(msg.author.id.toString(),msg)) return "You already have a system registered!";
    let system: System = new System(name);
    save(msg.author.id.toString(),system);
    return "System created!";
}

export function deleteSystem(msg: discord.Message, parsedMessage: string[]): string|void {
    if (!exists(msg.author.id.toString(),msg)) return "You don't have a system registered.";
    let system: System = load(msg.author.id.toString());
    msg.channel.send("Are you sure you want to delete your system?? Reply with the system id ("+system.id+") to delete.").then(a => {
        //@ts-ignore
        let c = (<discord.TextChannel>(a.channel)).createMessageCollector(a => a.author.id == msg.author.id,{time:30000}).on("collect", b => {
            c.stop();
            if (b.content != system.id) return;
            msg.author.createDM().then(dm => {
                dm.send({
                    files: [getSysExportMessage(msg.author.id)]
                }).then(message => {
                    dm.send(message.attachments.map(a=>a)[0].url);
                    Deno.removeSync("./systems/"+msg.author.id+"_export.json");
                    Deno.removeSync("./systems/"+msg.author.id+".json");
                    msg.channel.send("System deleted.").catch(err => {
                        sendError(msg,err);
                    });
                }).catch(err => {
                    sendError(msg,err);
                });
            }).catch(err => {
                sendError(msg,err);
            });
        });
    }).catch(err => {
        sendError(msg,err);
    });
}

export function listSystem(msg: discord.Message, parsedMessage: string[]) {
    if (exists(msg.author.id,msg)) {
        let system:System = load(msg.author.id.toString());
        let embed: discord.Embed  = new discord.Embed();

        embed.setTitle(system.name + " [`"+system.id+"`]");
        if (!isArrEmpty(system.members)) {
            system.members = system.members.sort((a,b) => {
                //@ts-ignore
                return (a.name > b.name)? 1: ((b.name > a.name)? -1: 0);
            });
            let str:string = "";
            let len = parseInt(parsedMessage[parsedMessage.length-1]);
            if (isNaN(len)) len = 1;
            len--;
            len = len*20;
            if (len > system.members.length) return "Page number too big.";
            for (let i = len; i < len + 20; i++) {
                if (i < system.members.length) {
                    let member: Member = system.members[i];
                    str += "[`"+member.id+"`] **"+member.name+"**";
                    if (!isArrEmpty(member.proxies)) str += " (`"+member.proxies[0].toProxyString()+"`)";
                    str += "\n";
                }
            }
            str.trim();
            embed.addField("Page "+(isNaN(parseInt(parsedMessage[parsedMessage.length-1]))? 1: parsedMessage[parsedMessage.length-1])+"/"+Math.ceil(system.members.length/25)+"",str);
        }
        msg.channel.send({
            //@ts-ignore
            embeds: [embed]
        });
    }
}

export function exportSystem(msg:discord.Message, parsedMessage: string[]) {
    if (fs.existsSync("./systems/"+msg.author.id+".json")) {
        msg.channel.send("Check your DMs :>");
        msg.author.createDM().then(channel => {
            //@ts-ignore
            channel.send({
                files: [getSysExportMessage(msg.author.id.toString())]
            }).then(message => {
                channel.send(message.attachments.map(a=>a)[0].url);
                Deno.removeSync("./systems/"+msg.author.id+"_export.json");
            }).catch(err => {
                sendError(msg,err);
            });
        }).catch(err => {
            sendError(msg,err);
        });;
        return;
    }
    msg.channel.send("No system registered.");
}

export function importSystem(msg:discord.Message, parsedMessage: string[]): string {
    if (parsedMessage.length > 1) {
        getData(parsedMessage[parsedMessage.length-1],"./systems/"+msg.author.id+".json");
        return "System imported.";
    }
    if (msg.attachments.map(a=>a).length > 0) {
        let url = msg.attachments.map(a=>a)[0].url;
        getData(url,"./systems/"+msg.author.id+".json");
        return "System imported.";
    }
    return "No system to import.";
}

function getSysExportMessage(id:string):discord.MessageAttachment {
    let system: System = load(id);
    saveExport(id,system);
    return new discord.MessageAttachment("./systems/"+id+"_export.json", "system.json");
}

export function getData(url:string,path:string) {
    Deno.writeTextFileSync(path,Deno.readTextFileSync(url));
}

export function autoOn(msg: discord.Message, parsedMessage: string[]): string {
    if (exists(msg.author.id.toString(),msg)) {
        let system = load(msg.author.id.toString());
        system.autobool = true;
        save(msg.author.id.toString(),system);
        return "Autoproxy enabled.";
    }
    return "System doesn't exist.";
}

export function autoOff(msg: discord.Message, parsedMessage: string[]): string {
    if (exists(msg.author.id.toString(),msg)) {
        let system = load(msg.author.id.toString());
        system.autobool = false;
        save(msg.author.id.toString(),system);
        return "Autoproxy disabled.";
    }
    return "System doesn't exist.";
}

export function spOn(msg: discord.Message, parsedMessage: string[]): string {
    if (exists(msg.author.id.toString(),msg)) {
        let system = load(msg.author.id.toString());
        //@ts-ignore
        system.serverProxy.put(msg.guildID,true);
        save(msg.author.id.toString(),system);
        return "Proxy has been enabled for this server.";
    }
    return "System doesn't exist.";
}

export function spOff(msg: discord.Message, parsedMessage: string[]): string {
    if (exists(msg.author.id.toString(),msg)) {
        let system = load(msg.author.id.toString());
        //@ts-ignore
        system.serverProxy.put(msg.guildID,false);
        save(msg.author.id.toString(),system);
        return "Proxy has been disabled for this server.";
    }
    return "System doesn't exist.";
}

export function setTag(msg: discord.Message, parsedMessage: string[]): string {
    parsedMessage.shift();
    parsedMessage.shift();
    let tag = parsedMessage.join(" ");
    if (!exists(msg.author.id.toString(),msg)) return "System doesn't exist.";
    let system: System = load(msg.author.id.toString());
    system.tag = tag;
    save(msg.author.id.toString(),system);
    return "System tag changed to `"+tag+"`!";
}

export function setAvatar(msg: discord.Message, parsedMessage: string[]): string {
    if (!exists(msg.author.id.toString(),msg)) return "System doesn't exist.";
    let system: System = load(msg.author.id.toString());
    let url: string;
    if (parsedMessage.length > 2)
        url = parsedMessage[parsedMessage.length-1]
    if (msg.attachments.map(a=>a).length > 0)
        url = msg.attachments.map(a=>a)[0].url;
    //@ts-ignore
    if (!url) return "No avatar to set.";
    system.avatar = url;
    save(msg.author.id.toString(),system);
    return "System avatar changed to `"+url+"`!";
}

export function setName(msg: discord.Message, parsedMessage: string[]): string {
    if (!exists(msg.author.id.toString(),msg)) return "System doesn't exist.";
    let system: System = load(msg.author.id.toString());
    parsedMessage.shift();
    parsedMessage.shift();
    let name = parsedMessage.join(" ");
    if (!name) return "No name specified.";
    system.name = name;
    save(msg.author.id.toString(),system);
    return "System name set to \"" + name + "\"";
}

export function setDesc(msg: discord.Message, parsedMessage: string[]): string {
    if (!exists(msg.author.id.toString(),msg)) return "System doesn't exist.";
    let system: System = load(msg.author.id.toString());
    parsedMessage.shift();
    parsedMessage.shift();
    let name = parsedMessage.join(" ");
    if (!name) return "No description specified.";
    system.description = name;
    save(msg.author.id.toString(),system);
    return "System description set to \"" + name + "\"";
}