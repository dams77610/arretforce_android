package fr.outil.nettoyage;
interface ICleanerService {
  String getProcessSnapshot();
  String stopPackages(in String[] packages);
}
