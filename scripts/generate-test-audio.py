from pathlib import Path
import subprocess

out = Path('test-audio')
out.mkdir(exist_ok=True)
formats = [('01-first.mp3', 'libmp3lame'), ('02-wave.flac', 'flac'), ('03-wind.wav', 'pcm_s16le'), ('04-night.ogg', 'libvorbis'), ('05-orbit.opus', 'libopus'), ('06-rain.m4a', 'aac')]
for index, (name, codec) in enumerate(formats):
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i', f'sine=frequency={220 + index*55}:duration=60', '-c:a', codec, '-threads', '1', '-metadata', f'title=Lad Test {index+1}', '-metadata', 'artist=Lad Test', '-metadata', 'album=Integration Fixtures', '-metadata', 'genre=Ambient', str(out / name)], check=True)
print(out.resolve())

for name, color in [('01-first.mp3', 'red'), ('02-wave.flac', 'blue')]:
    cover = out / f'{color}.png'
    decorated = out / f'covered-{name}'
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'lavfi', '-i',
        f'color=c={color}:s=128x128', '-frames:v', '1', '-threads', '1', str(cover)], check=True)
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(out/name),
        '-i', str(cover), '-map', '0:a', '-map', '1:v', '-c', 'copy', '-disposition:v', 'attached_pic',
        str(decorated)], check=True)
    decorated.replace(out/name)
    cover.unlink()
