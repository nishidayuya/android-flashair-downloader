#!/usr/bin/env ruby
# frozen_string_literal: true

# A stand-in for a FlashAir card, so that the app can be exercised end to end
# without one (docs/design.md 10, "疑似実機").
#
# It serves a directory tree from disk and speaks the parts of the FlashAir HTTP
# API the app uses: command.cgi ops 100/101/102/104/108/120/140, plain GETs for
# file contents, and thumbnail.cgi.
#
#   ruby tools/flashair-stub.rb --root tools/fixtures/card --port 8080
#
# Deliberate quirks, because the app has to cope with them on a real card:
#
# - responses use CRLF and start with the WLANSD_FILELIST header line
# - dates and times are FAT encoded from the file's mtime
# - Range requests are ignored unless --ranges is passed: resume support is
#   unverified on real cards, and the app must not depend on it
# - op=102 (write status) clears itself once read

require "base64"
require "optparse"
require "socket"
require "uri"

# FAT attribute bits (docs/design.md 2.3).
ATTRIBUTE_DIRECTORY = 0x10
ATTRIBUTE_ARCHIVE = 0x20

SECTOR_SIZE = 512
TOTAL_SECTORS = 31_088_640

# A 1x1 pixel JPEG, returned for every thumbnail.cgi request.
THUMBNAIL_JPEG = Base64.decode64(<<~BASE64)
  /9j/4AAQSkZJRgABAQEAYABgAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0a
  HBwcJC4nICIsIxwcKDcpLDA1NDQ0Hyc5PTgyPDUzNDP/wAALCAABAAEBAREA/8QAFAABAQAAAAAA
  AAAAAAAAAAAAAAr/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/a
  AAwDAQACEQMRAD8AmQA//9k=
BASE64

REASONS = {
  200 => "OK",
  206 => "Partial Content",
  400 => "Bad Request",
  404 => "Not Found",
}.freeze

class FlashAirStub
  def initialize(root:, ssid:, firmware:, cid:, ranges:)
    @root = File.expand_path(root)
    @ssid = ssid
    @firmware = firmware
    @cid = cid
    @ranges = ranges
    @write_status = "1"
    raise ArgumentError, "no such directory: #{@root}" unless File.directory?(@root)
  end

  def serve(connection)
    request_line = connection.gets
    return if request_line.nil?

    headers = read_headers(connection)
    warn("> #{request_line.strip}")
    method, target, = request_line.split(" ")
    return respond(connection, 400, "") unless method == "GET"

    uri = URI.parse(target)
    case URI.decode_www_form_component(uri.path)
    when "/command.cgi" then command(connection, uri.query)
    when "/thumbnail.cgi" then thumbnail(connection, uri.query)
    else file(connection, URI.decode_www_form_component(uri.path), headers)
    end
  end

  private

  def read_headers(connection)
    headers = {}
    while (line = connection.gets) && line != "\r\n"
      name, value = line.split(":", 2)
      headers[name.to_s.strip.downcase] = value.to_s.strip
    end
    headers
  end

  # Resolves a request path to a path inside the root, refusing to escape it.
  def resolve(path)
    full = File.expand_path(File.join(@root, path.to_s.sub(%r{\A/}, "")))
    return nil unless full == @root || full.start_with?("#{@root}/")

    full
  end

  def fat_date_time(time)
    date = ((time.year - 1980) << 9) | (time.month << 5) | time.day
    clock = (time.hour << 11) | (time.min << 5) | (time.sec / 2)
    [date, clock]
  end

  def command(connection, query)
    params = URI.decode_www_form(query.to_s).to_h
    body = case params["op"]
           when "100" then listing(params["DIR"] || "/")
           when "101" then entry_count(params["DIR"] || "/")
           when "102" then take_write_status
           when "104" then @ssid
           when "108" then @firmware
           when "120" then @cid
           when "140" then free_space
           end
    body ? respond(connection, 200, body) : respond(connection, 404, "")
  end

  def listing(directory)
    full = resolve(directory)
    return nil unless full && File.directory?(full)

    # For DIR=/ a real card leaves the directory field empty.
    reported = directory == "/" ? "" : directory.chomp("/")
    lines = Dir.children(full).sort.map do |name|
      stat = File.stat(File.join(full, name))
      date, clock = fat_date_time(stat.mtime)
      attribute = stat.directory? ? ATTRIBUTE_DIRECTORY : ATTRIBUTE_ARCHIVE
      size = stat.directory? ? 0 : stat.size
      "#{reported},#{name},#{size},#{attribute},#{date},#{clock}"
    end
    (["WLANSD_FILELIST"] + lines).join("\r\n") + "\r\n"
  end

  def entry_count(directory)
    full = resolve(directory)
    full && File.directory?(full) ? Dir.children(full).size.to_s : nil
  end

  def take_write_status
    status = @write_status
    @write_status = "0"
    status
  end

  def free_space
    used = Dir.glob(File.join(@root, "**/*")).sum { |path| File.file?(path) ? File.size(path) : 0 }
    "#{TOTAL_SECTORS - (used / SECTOR_SIZE)}/#{TOTAL_SECTORS},#{SECTOR_SIZE}"
  end

  def thumbnail(connection, query)
    # The query string is the file path itself, not a name=value pair.
    path = resolve(URI.decode_www_form_component(query.to_s))
    if path && File.file?(path) && File.extname(path).downcase.match?(/\.jpe?g\z/)
      respond(connection, 200, THUMBNAIL_JPEG, content_type: "image/jpeg")
    else
      respond(connection, 404, "")
    end
  end

  def file(connection, path, headers)
    full = resolve(path)
    return respond(connection, 404, "") unless full && File.file?(full)

    content = File.binread(full)
    match = @ranges ? headers["range"].to_s.match(/bytes=(\d+)-/) : nil
    if match
      offset = match[1].to_i
      tail = content.byteslice(offset..) || ""
      respond(connection, 206, tail,
              content_type: "application/octet-stream",
              headers: {
                "Content-Range" => "bytes #{offset}-#{content.bytesize - 1}/#{content.bytesize}",
              })
    else
      respond(connection, 200, content, content_type: "application/octet-stream")
    end
  end

  def respond(connection, code, body, content_type: "text/plain", headers: {})
    connection.write("HTTP/1.1 #{code} #{REASONS.fetch(code)}\r\n")
    connection.write("Content-Type: #{content_type}\r\n")
    connection.write("Content-Length: #{body.bytesize}\r\n")
    headers.each { |name, value| connection.write("#{name}: #{value}\r\n") }
    connection.write("Connection: close\r\n\r\n")
    connection.write(body)
  end
end

options = {
  root: File.expand_path("fixtures/card", __dir__),
  port: 8080,
  host: "0.0.0.0",
  ranges: false,
  ssid: "flashair_STUB",
  firmware: "F19BAW3AW2.00.00",
  cid: "0123456789ABCDEF0123456789ABCDEF",
}

OptionParser.new do |parser|
  parser.on("--root PATH", "directory to serve as the card's storage")
  parser.on("--port PORT", Integer)
  parser.on("--host HOST")
  parser.on("--ranges", "answer Range requests with 206 instead of ignoring them")
  parser.on("--ssid SSID")
  parser.on("--firmware VERSION")
  parser.on("--cid CID")
end.parse!(into: options)

stub = FlashAirStub.new(
  root: options[:root],
  ssid: options[:ssid],
  firmware: options[:firmware],
  cid: options[:cid],
  ranges: options[:ranges],
)

server = TCPServer.new(options[:host], options[:port])
warn("FlashAir stub serving #{File.expand_path(options[:root])} " \
     "on http://#{options[:host]}:#{options[:port]}")

loop do
  connection = server.accept
  Thread.new(connection) do |socket|
    stub.serve(socket)
  rescue StandardError => e
    warn("! #{e.class}: #{e.message}")
  ensure
    socket.close
  end
end
